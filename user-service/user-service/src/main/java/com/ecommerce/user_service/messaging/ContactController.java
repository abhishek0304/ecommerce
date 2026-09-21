package com.ecommerce.user_service.messaging;
import com.ecommerce.user_service.repository.*;
import com.ecommerce.user_service.entity.AccountStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@RestController
public class ContactController {
    private final UserRepository users;private final UserPreferencesRepository preferences;
    public ContactController(UserRepository users,UserPreferencesRepository preferences) {this.users=users;this.preferences=preferences;}
    public record Contact(boolean active,String email,String phone,boolean emailNotifications,boolean smsNotifications,boolean whatsAppNotifications) {}
    @GetMapping("/internal/users/{id}/contact") @Transactional(readOnly=true)
    public Contact get(@PathVariable Long id) {
        var user=users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var p=preferences.findByUser(user).orElse(null);
        return new Contact(user.isActive() && user.getStatus()==AccountStatus.ACTIVE,user.getEmail(),user.getPhone(),
            p!=null && p.isEmailNotifications(),p!=null && p.isSmsNotifications(),p!=null && p.isWhatsAppNotifications());
    }
}
