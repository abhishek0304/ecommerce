package com.ecommerce.user_service.messaging;
import com.ecommerce.user_service.entity.User;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
public class AccountMessages {
    private final AccountMessageRepository messages;
    public AccountMessages(AccountMessageRepository messages) {this.messages=messages;}
    @Transactional(propagation=Propagation.MANDATORY)
    public void otp(User user,String otp,Instant expiry,boolean reset) {
        AccountMessage m=new AccountMessage();m.id=UUID.randomUUID().toString();m.userId=user.getId();m.recipient=user.getEmail();
        m.subject=reset?"Reset your ecommerce password":"Verify your ecommerce email";
        m.body=(reset?"Your password reset code is ":"Your email verification code is ")+otp+". It expires in 10 minutes. Never share this code. If you did not request it, ignore this email.";
        m.expiresAt=expiry;messages.save(m);
    }
}
