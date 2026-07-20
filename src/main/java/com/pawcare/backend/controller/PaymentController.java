package com.pawcare.backend.controller;

import java.util.Map;
import java.util.Optional;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pawcare.backend.entity.Booking;
import com.pawcare.backend.entity.Payment;
import com.pawcare.backend.entity.ProviderProfile;
import com.pawcare.backend.repository.BookingRepository;
import com.pawcare.backend.repository.PaymentRepository;
import com.pawcare.backend.repository.ProviderProfileRepository;
import com.pawcare.backend.service.AuditLogService;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final BookingRepository bookingRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final PaymentRepository paymentRepository;
    private final AuditLogService auditLogService;
    private final RazorpayClient razorpayClient;

    @Value("${razorpay.key-id}")
    private String razorpayKeyId;

    @Value("${razorpay.key-secret}")
    private String razorpayKeySecret;

    public PaymentController(BookingRepository bookingRepository,
                              ProviderProfileRepository providerProfileRepository,
                              PaymentRepository paymentRepository,
                              AuditLogService auditLogService,
                              RazorpayClient razorpayClient) {
        this.bookingRepository = bookingRepository;
        this.providerProfileRepository = providerProfileRepository;
        this.paymentRepository = paymentRepository;
        this.auditLogService = auditLogService;
        this.razorpayClient = razorpayClient;
    }

    @PreAuthorize("hasRole('OWNER')")
    @PostMapping("/bookings/{bookingId}/checkout")
    public ResponseEntity<?> createCheckout(@PathVariable String bookingId, Authentication auth) {
        Optional<Booking> bookingOpt = bookingRepository.findById(bookingId);
        if (bookingOpt.isEmpty()) return ResponseEntity.status(404).body("Booking not found");

        Booking booking = bookingOpt.get();
        if (!booking.getOwnerId().equals(auth.getName())) {
            return ResponseEntity.status(403).body("Not your booking");
        }

        Optional<ProviderProfile> providerOpt = providerProfileRepository.findByUserId(booking.getProviderId());
        if (providerOpt.isEmpty()) {
            return ResponseEntity.status(400).body("Provider profile not found");
        }

        // Guard against a provider who hasn't set an hourly rate yet — otherwise
        // this silently produces a 0-paise order, which Razorpay will reject
        // (min order amount is 100 paise / ₹1).
        Double hourlyRate = providerOpt.get().getHourlyRate();
        if (hourlyRate == null || hourlyRate <= 0) {
            return ResponseEntity.status(400).body("This provider hasn't set an hourly rate yet");
        }

        Optional<Payment> existingPayment = paymentRepository.findAll().stream()
                .filter(p -> bookingId.equals(p.getBookingId()))
                .findFirst();

        if (existingPayment.isPresent()) {
            Payment existing = existingPayment.get();
            if ("PAID".equals(existing.getStatus())) {
                return ResponseEntity.status(409).body("This booking is already paid");
            }

            // Don't blindly hand back a possibly-stale order_id. Ask Razorpay
            // whether it's still usable before reusing it — an order that's
            // been retried/expired can make checkout.js fail with a 500 even
            // though the key pair itself is fine.
            try {
                com.razorpay.Order existingOrder = razorpayClient.orders.fetch(existing.getRazorpayOrderId());
                String orderStatus = existingOrder.get("status"); // "created", "attempted", or "paid"
                if ("created".equals(orderStatus) || "attempted".equals(orderStatus)) {
                    return ResponseEntity.ok(Map.of(
                            "orderId", existing.getRazorpayOrderId(),
                            "amount", Math.round(existing.getAmount() * 100),
                            "currency", "INR",
                            "keyId", razorpayKeyId
                    ));
                }
                // any other status — fall through below and issue a fresh order
            } catch (RazorpayException e) {
                // Lookup itself failed — don't risk reusing a broken order,
                // fall through and create a new one instead.
                System.err.println("Could not verify existing Razorpay order " +
                        existing.getRazorpayOrderId() + ": " + e.getMessage());
            }
        }

        double hours = java.time.Duration.between(booking.getScheduledStart(), booking.getScheduledEnd()).toMinutes() / 60.0;
        long amountPaise = Math.round(hourlyRate * hours * 100);
        long platformFeePaise = Math.round(amountPaise * 0.10);

        if (amountPaise < 100) {
            // Razorpay's hard minimum for an INR order is 100 paise (₹1).
            return ResponseEntity.status(400).body("Booking amount is too small to charge (minimum ₹1)");
        }

        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", amountPaise);
        orderRequest.put("currency", "INR");
        orderRequest.put("receipt", bookingId);

        com.razorpay.Order order;
        try {
            order = razorpayClient.orders.create(orderRequest);
        } catch (RazorpayException e) {
            // Surface a real message instead of a raw 500 / stack trace. Most
            // common causes: RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET missing,
            // blank, mismatched, or containing stray whitespace.
            System.err.println("Razorpay order creation failed for booking " + bookingId +
                    ": " + e.getMessage() +
                    (e.getCause() != null ? " | cause: " + e.getCause().getMessage() : ""));
            return ResponseEntity.status(502).body(
                    "Could not reach the payment gateway. Please try again in a moment.");
        } catch (org.json.JSONException e) {
            // Razorpay responded with something that wasn't valid JSON —
            // almost always an auth/config problem.
            System.err.println("Razorpay returned a non-JSON response for booking " + bookingId +
                    ": " + e.getMessage());
            return ResponseEntity.status(502).body(
                    "Payment gateway configuration error. Please contact support.");
        }

        String orderId = order.get("id");

        // Update the existing row if there was one (avoids a duplicate payment
        // record for the same booking); otherwise create a new one.
        Payment payment = existingPayment.orElse(new Payment());
        payment.setBookingId(bookingId);
        payment.setAmount(amountPaise / 100.0);
        payment.setPlatformFee(platformFeePaise / 100.0);
        payment.setProviderPayout((amountPaise - platformFeePaise) / 100.0);
        payment.setStatus("PENDING");
        payment.setRazorpayOrderId(orderId);
        paymentRepository.save(payment);

        auditLogService.log(auth.getName(), "PAYMENT_CHECKOUT_CREATED", "Booking", bookingId);

        return ResponseEntity.ok(Map.of(
                "orderId", orderId,
                "amount", amountPaise,
                "currency", "INR",
                "keyId", razorpayKeyId
        ));
    }

    @PreAuthorize("hasRole('OWNER')")
    @PostMapping("/verify")
    public ResponseEntity<?> verifyPayment(@RequestBody Map<String, String> body, Authentication auth) throws RazorpayException {
        String razorpayOrderId = body.get("razorpay_order_id");
        String razorpayPaymentId = body.get("razorpay_payment_id");
        String razorpaySignature = body.get("razorpay_signature");

        Optional<Payment> paymentOpt = paymentRepository.findAll().stream()
                .filter(p -> razorpayOrderId.equals(p.getRazorpayOrderId()))
                .findFirst();
        if (paymentOpt.isEmpty()) return ResponseEntity.status(404).body("Payment not found");

        JSONObject options = new JSONObject();
        options.put("razorpay_order_id", razorpayOrderId);
        options.put("razorpay_payment_id", razorpayPaymentId);
        options.put("razorpay_signature", razorpaySignature);

        boolean valid = Utils.verifyPaymentSignature(options, razorpayKeySecret);
        if (!valid) {
            return ResponseEntity.status(400).body("Payment verification failed");
        }

        Payment payment = paymentOpt.get();
        payment.setStatus("PAID");
        payment.setRazorpayPaymentId(razorpayPaymentId);
        paymentRepository.save(payment);

        auditLogService.log(auth.getName(), "PAYMENT_VERIFIED", "Booking", payment.getBookingId());

        // The payout ledger entry is created in RazorpayWebhookController, triggered
        // by the payment.captured event — not here. That's the authoritative source
        // (works even if the browser never reaches this endpoint), and payment.setPayoutRecorded
        // guards against it happening twice if the webhook fires more than once.

        return ResponseEntity.ok(Map.of("status", "PAID"));
    }
}