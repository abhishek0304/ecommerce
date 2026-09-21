package com.ecommerce.order_service.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class RazorpayClient {
    private final RazorpayApi api;
    private final String authorization;
    private final String keyId;
    private final String secret;
    private final String webhookSecret;
    public RazorpayClient(RazorpayApi api,
            @Value("${razorpay.key-id:}") String keyId, @Value("${razorpay.key-secret:}") String secret,
            @Value("${razorpay.webhook-secret:}") String webhookSecret) {
        this.keyId = keyId; this.secret = secret; this.webhookSecret = webhookSecret;
        this.api = api;
        this.authorization = "Basic " + org.springframework.http.HttpHeaders.encodeBasicAuth(keyId, secret, StandardCharsets.ISO_8859_1);
    }
    public void requireConfigured() {
        if (!keyId.startsWith("rzp_test_") || secret.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Configure Razorpay test keys to use online payments");
    }
    public String keyId() { return keyId; }
    public JsonNode create(String receipt, long amount) {
        requireConfigured();
        return api.create(authorization, Map.of("receipt", receipt, "amount", amount, "currency", "INR", "partial_payment", false));
    }
    public JsonNode findByReceipt(String receipt) {
        requireConfigured();
        JsonNode result = api.find(authorization, receipt, 100);
        if (result == null || !result.path("items").isArray()) throw new IllegalStateException("Invalid Razorpay response");
        JsonNode found = null;
        for (JsonNode item : result.path("items")) {
            if (receipt.equals(item.path("receipt").asText())) {
                if (found != null) throw new IllegalStateException("Multiple Razorpay orders for one receipt; reconciliation required");
                found = item;
            }
        }
        return found;
    }
    public JsonNode order(String id) { return api.order(authorization, id); }
    public JsonNode payments(String id) { return api.payments(authorization, id); }
    public JsonNode createRefund(String paymentId, long amount, String attemptId) {
        requireConfigured();
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("amount", amount); body.put("speed", "normal"); body.put("receipt", attemptId);
        return api.createRefund(authorization, paymentId, attemptId, body);
    }
    public JsonNode refund(String id) { requireConfigured(); return api.refund(authorization, id); }
    public JsonNode payment(String id) { return api.payment(authorization, id); }
    public void verifySignature(String orderId, String paymentId, String signature) {
        requireConfigured();
        if (!matches(secret, (orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8), signature))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment signature");
    }
    public void verifyWebhook(byte[] body, String signature) {
        if (webhookSecret.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Razorpay webhook secret is not configured");
        if (!matches(webhookSecret, body, signature)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook signature");
    }
    private boolean matches(String key, byte[] message, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return MessageDigest.isEqual(mac.doFinal(message), HexFormat.of().parseHex(signature));
        } catch (IllegalArgumentException ex) { return false; }
        catch (Exception ex) { throw new IllegalStateException("Cannot verify signature", ex); }
    }
}
