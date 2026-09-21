package com.ecommerce.user_service.repository;
import com.ecommerce.user_service.entity.*; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken,Long>{ Optional<PasswordResetToken> findFirstByUserAndOtpAndUsedFalseOrderByIdDesc(User user,String otp); Optional<PasswordResetToken> findFirstByUserOrderByIdDesc(User user); }
