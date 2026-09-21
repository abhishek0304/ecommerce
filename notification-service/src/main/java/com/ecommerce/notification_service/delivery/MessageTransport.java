package com.ecommerce.notification_service.delivery;
public interface MessageTransport {
    boolean enabled(Delivery.Channel channel);
    String send(Delivery delivery);
    class Rejected extends RuntimeException {
        public final boolean retryable;
        public Rejected(boolean retryable) {super("Provider rejected the request");this.retryable=retryable;}
    }
}
