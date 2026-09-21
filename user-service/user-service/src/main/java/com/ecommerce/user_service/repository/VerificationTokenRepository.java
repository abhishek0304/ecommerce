package com.ecommerce.user_service.repository;
import com.ecommerce.user_service.entity.*; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface VerificationTokenRepository extends JpaRepository<VerificationToken,Long>{ Optional<VerificationToken> findFirstByUserAndOtpAndUsedFalseOrderByIdDesc(User user,String otp); Optional<VerificationToken> findFirstByUserOrderByIdDesc(User user); }
