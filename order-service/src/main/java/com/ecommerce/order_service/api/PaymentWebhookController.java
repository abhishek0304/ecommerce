package com.ecommerce.order_service.api;
import com.ecommerce.order_service.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController
public class PaymentWebhookController {
    private final OrderService service;
    public PaymentWebhookController(OrderService service) { this.service = service; }
    @PostMapping("/api/payments/razorpay/webhook")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void webhook(@RequestBody byte[] body, @RequestHeader("X-Razorpay-Signature") String signature) {
        service.webhook(body, signature);
    }
}
