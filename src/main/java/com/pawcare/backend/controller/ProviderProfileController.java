package com.pawcare.backend.controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.pawcare.backend.dto.ProviderProfileRequest;
import com.pawcare.backend.dto.ProviderProfileResponse;
import com.pawcare.backend.dto.RazorpayLinkedAccountRequest;
import com.pawcare.backend.entity.ProviderProfile;
import com.pawcare.backend.repository.ProviderProfileRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/providers")
public class ProviderProfileController {

    private final ProviderProfileRepository providerProfileRepository;
    private final com.pawcare.backend.repository.UserRepository userRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${razorpay.key-id}")
    private String keyId;

    @Value("${razorpay.key-secret}")
    private String keySecret;

    public ProviderProfileController(ProviderProfileRepository providerProfileRepository,
                                  com.pawcare.backend.repository.UserRepository userRepository) {
        this.providerProfileRepository = providerProfileRepository;
        this.userRepository = userRepository;
    }

    @PreAuthorize("hasRole('PROVIDER')")
    @PostMapping("/me")
    public ResponseEntity<?> upsertOwnProfile(@Valid @RequestBody ProviderProfileRequest req, Authentication auth) {
        String userId = auth.getName();
        ProviderProfile profile = providerProfileRepository.findByUserId(userId)
                .orElse(new ProviderProfile(null, userId, null, null, null, false, 0.0));

        profile.setServiceTypes(req.serviceTypes());
        profile.setHourlyRate(req.hourlyRate());
        providerProfileRepository.save(profile);

        return ResponseEntity.ok(ProviderProfileResponse.from(profile, userRepository.findById(userId).orElse(null)));
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam(required = false) String serviceType) {
        List<ProviderProfile> results = (serviceType == null || serviceType.isBlank())
                ? providerProfileRepository.findAll()
                : providerProfileRepository.findByServiceTypesContaining(serviceType);

        List<String> userIds = results.stream().map(ProviderProfile::getUserId).toList();
        Map<String, com.pawcare.backend.entity.User> usersById = userRepository.findAllById(userIds).stream()
                .collect(java.util.stream.Collectors.toMap(com.pawcare.backend.entity.User::getId, u -> u));

        return ResponseEntity.ok(results.stream()
                .map(p -> ProviderProfileResponse.from(p, usersById.get(p.getUserId())))
                .toList());
    }

    private HttpHeaders razorpayAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(keyId, keySecret);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @PreAuthorize("hasRole('PROVIDER')")
    @PostMapping("/me/razorpay/linked-account")
    public ResponseEntity<?> createLinkedAccount(@Valid @RequestBody RazorpayLinkedAccountRequest req, Authentication auth) {
        String userId = auth.getName();
        ProviderProfile profile = providerProfileRepository.findByUserId(userId)
                .orElse(new ProviderProfile(null, userId, null, null, null, false, 0.0));

        if (profile.getRazorpayAccountId() != null) {
            return ResponseEntity.status(409).body("Payout account already submitted");
        }

        Map<String, Object> body = Map.of(
                "email", req.email(),
                "phone", req.phone(),
                "type", "route",
                "legal_business_name", req.businessName(),
                "business_type", "individual",
                "contact_name", req.businessName(),
                "profile", Map.of("category", "services", "subcategory", "pet_services"),
                "legal_info", Map.of("pan", req.pan())
        );

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://api.razorpay.com/v1/accounts",
                    HttpMethod.POST,
                    new HttpEntity<>(body, razorpayAuthHeaders()),
                    Map.class
            );

            String accountId = (String) response.getBody().get("id");
            profile.setRazorpayAccountId(accountId);
            providerProfileRepository.save(profile);

            return ResponseEntity.ok(Map.of("accountId", accountId, "status", "created"));

        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }
    @PreAuthorize("hasRole('PROVIDER')")
    @GetMapping("/me/razorpay/status")
    public ResponseEntity<?> razorpayStatus(Authentication auth) {
        Optional<ProviderProfile> profileOpt = providerProfileRepository.findByUserId(auth.getName());
        if (profileOpt.isEmpty() || profileOpt.get().getRazorpayAccountId() == null) {
            return ResponseEntity.ok(Map.of("connected", false, "activated", false));
        }

        ResponseEntity<Map> response = restTemplate.exchange(
                "https://api.razorpay.com/v1/accounts/" + profileOpt.get().getRazorpayAccountId(),
                HttpMethod.GET,
                new HttpEntity<>(razorpayAuthHeaders()),
                Map.class
        );

        String status = response.getBody().get("status") == null ? "created" : response.getBody().get("status").toString();
        boolean activated = "activated".equals(status);

        return ResponseEntity.ok(Map.of("connected", true, "activated", activated, "status", status));
    }
}