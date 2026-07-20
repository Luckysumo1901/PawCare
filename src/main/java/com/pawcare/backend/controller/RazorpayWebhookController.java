package com.pawcare.backend.controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.pawcare.backend.entity.Payment;
import com.pawcare.backend.entity.PayoutLedger;
import com.pawcare.backend.repository.BookingRepository;
import com.pawcare.backend.repository.PaymentRepository;
import com.pawcare.backend.repository.PayoutLedgerRepository;
import com.pawcare.backend.repository.ProviderProfileRepository;
import com.pawcare.backend.service.AuditLogService;

@RestController
@RequestMapping("/webhooks")
public class RazorpayWebhookController {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final PayoutLedgerRepository payoutLedgerRepository;
    private final AuditLogService auditLogService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${razorpay.webhook-secret}")
    private String webhookSecret;

    @Value("${razorpay.key-id}")
    private String razorpayKeyId;

    @Value("${razorpay.key-secret}")
    private String razorpayKeySecret;

    public RazorpayWebhookController(PaymentRepository paymentRepository,
                                      BookingRepository bookingRepository,
                                      ProviderProfileRepository providerProfileRepository,
                                      PayoutLedgerRepository payoutLedgerRepository,
                                      AuditLogService auditLogService) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.providerProfileRepository = providerProfileRepository;
        this.payoutLedgerRepository = payoutLedgerRepository;
        this.auditLogService = auditLogService;
    }

    @PostMapping("/razorpay")
    public ResponseEntity<?> handleWebhook(@RequestBody String payload,
                                            @RequestHeader("X-Razorpay-Signature") String signature) {
        if (!isValidSignature(payload, signature)) {
            return ResponseEntity.status(400).body("Invalid signature");
        }

        JSONObject event = new JSONObject(payload);
        if ("payment.captured".equals(event.optString("event"))) {
            JSONObject entity = event.getJSONObject("payload").getJSONObject("payment").getJSONObject("entity");
            String razorpayPaymentId = entity.optString("id");
            String razorpayOrderId = entity.optString("order_id");

            Optional<Payment> paymentOpt = paymentRepository.findAll().stream()
                    .filter(p -> razorpayOrderId.equals(p.getRazorpayOrderId()))
                    .findFirst();

            paymentOpt.ifPresent(payment -> {
                payment.setStatus("PAID");
                payment.setRazorpayPaymentId(razorpayPaymentId);
                paymentRepository.save(payment);
                auditLogService.log("RAZORPAY_WEBHOOK", "PAYMENT_CAPTURED", "Payment", payment.getId());

                if (Boolean.TRUE.equals(payment.getPayoutRecorded())) {
                    return; // already recorded — Razorpay can redeliver the same event
                }

                bookingRepository.findById(payment.getBookingId()).ifPresent(booking -> {
                    PayoutLedger ledgerEntry = new PayoutLedger(
                            null, booking.getProviderId(), booking.getId(), payment.getId(),
                            payment.getProviderPayout(), "PENDING_PAYOUT", null, null
                    );
                    payoutLedgerRepository.save(ledgerEntry);

                    providerProfileRepository.findByUserId(booking.getProviderId()).ifPresent(provider -> {
                        if (provider.getRazorpayAccountId() != null) {
                            createRouteTransfer(razorpayPaymentId, provider.getRazorpayAccountId(),
                                    Math.round(payment.getProviderPayout() * 100));
                        }
                    });
                });

                payment.setPayoutRecorded(true);
                paymentRepository.save(payment);
            });
        }

        return ResponseEntity.ok().build();
    }

    private void createRouteTransfer(String razorpayPaymentId, String providerAccountId, long amountPaise) {
        try {
            Map<String, Object> transfer = Map.of(
                    "account", providerAccountId,
                    "amount", amountPaise,
                    "currency", "INR"
            );
            Map<String, Object> body = Map.of("transfers", List.of(transfer));

            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(razorpayKeyId, razorpayKeySecret);
            headers.setContentType(MediaType.APPLICATION_JSON);

            restTemplate.exchange(
                    "https://api.razorpay.com/v1/payments/" + razorpayPaymentId + "/transfers",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );
        } catch (Exception e) {
            System.err.println("Route transfer failed for payment " + razorpayPaymentId + ": " + e.getMessage());
        }
    }

    private boolean isValidSignature(String payload, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            byte[] calculatedBytes = hex.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] signatureBytes = signature.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return java.security.MessageDigest.isEqual(calculatedBytes, signatureBytes);
        } catch (Exception e) {
            return false;
        }
    }
}