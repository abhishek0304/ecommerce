package com.ecommerce.notification_service.delivery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
@Service
public class RecipientClient {
    public record Contact(boolean active,String email,String phone,boolean emailNotifications,boolean smsNotifications,boolean whatsAppNotifications) {
        public boolean permits(Delivery.Channel c) {return active && switch(c) {case EMAIL -> emailNotifications;case SMS -> smsNotifications;case WHATSAPP -> whatsAppNotifications;};}
    }
    private final UserContactApi client;
    private final String key;
    public RecipientClient(UserContactApi client,
            @Value("${internal.service-key:local-development-service-key-change-me}") String key) {
        this.client=client; this.key=key;
    }
    public Contact get(Long id) {return client.get(id,key);}
}
