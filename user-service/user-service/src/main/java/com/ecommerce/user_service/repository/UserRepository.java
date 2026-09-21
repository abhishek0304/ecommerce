package com.ecommerce.user_service.repository;
import com.ecommerce.user_service.entity.User; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface UserRepository extends JpaRepository<User,Long>{
    Optional<User> findByEmailIgnoreCase(String email); boolean existsByEmailIgnoreCase(String email); boolean existsByPhone(String phone);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where lower(u.email)=lower(:email)")
    Optional<User> lockByEmail(String email);
}
