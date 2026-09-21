package com.ecommerce.user_service;
import com.ecommerce.user_service.messaging.*;
import com.ecommerce.user_service.entity.*;
import com.ecommerce.user_service.repository.*;
import com.ecommerce.user_service.dto.requests.*;
import com.ecommerce.user_service.service.UserService;
import com.ecommerce.user_service.exception.ApiException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc
class AccountMessageTests {
    @Autowired UserService service;@Autowired UserRepository users;@Autowired AccountMessageRepository messages;
    @Autowired AccountMessages writer;@Autowired VerificationTokenRepository tokens;@Autowired TransactionTemplate tx;@Autowired MockMvc mvc;
    User create() {
        RegisterRequest r=new RegisterRequest();r.setName("Test Customer");r.setEmail(UUID.randomUUID()+"@example.com");r.setPhone("+91"+String.format("%010d",Math.abs(UUID.randomUUID().getMostSignificantBits()%10000000000L)));r.setPassword("Password123!");
        service.register(r);return users.findByEmailIgnoreCase(r.getEmail()).orElseThrow();
    }
    @Test void registrationQueuesActualOtpAndCooldownPreventsImmediateResend() {
        User u=create();var token=tokens.findFirstByUserOrderByIdDesc(u).orElseThrow();
        assertThat(messages.findAll()).filteredOn(m -> m.userId.equals(u.getId())).singleElement().satisfies(m -> {assertThat(m.body).contains(token.getOtp());assertThat(m.recipient).isEqualTo(u.getEmail());});
        assertThatThrownBy(() -> service.resendVerification(u.getEmail())).isInstanceOf(ApiException.class);
    }
    @Test void messageOutboxRollsBackWithAccountTransaction() {
        User u=create();long before=messages.count();
        tx.executeWithoutResult(s -> {writer.otp(u,"999999",Instant.now().plusSeconds(600),true);s.setRollbackOnly();});
        assertThat(messages.count()).isEqualTo(before);
    }
    @Test void resetQueuesEmailAndRateLimitsRepeat() {
        User u=create();var r=new PasswordRequests.Forgot();r.setEmail(u.getEmail());service.forgot(r);
        assertThat(messages.findAll()).filteredOn(m -> m.userId.equals(u.getId()) && m.subject.contains("Reset")).hasSize(1);
        assertThatThrownBy(() -> service.forgot(r)).isInstanceOf(ApiException.class);
    }
    @Test void contactIsOnlyAccessibleWithInternalServiceKey() throws Exception {
        User u=create();mvc.perform(get("/internal/users/"+u.getId()+"/contact")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/users/"+u.getId()+"/contact").header("X-Service-Key","wrong")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/users/"+u.getId()+"/contact").header("X-Service-Key","local-development-service-key-change-me"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(u.getEmail())).andExpect(jsonPath("$.whatsAppNotifications").value(false));
    }
}
