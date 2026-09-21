package com.ecommerce.user_service.repository;
import com.ecommerce.user_service.entity.*; import org.springframework.data.jpa.repository.*; import java.util.*;
public interface RefreshTokenRepository extends JpaRepository<RefreshToken,Long>{ Optional<RefreshToken> findByToken(String token); List<RefreshToken> findByUserAndRevokedFalse(User user); @Modifying @Query("update RefreshToken t set t.revoked=true where t.user=:user") void revokeAll(User user); }
