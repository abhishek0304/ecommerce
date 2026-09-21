package com.ecommerce.user_service.repository;
import com.ecommerce.user_service.entity.*; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface UserPreferencesRepository extends JpaRepository<UserPreferences,Long>{ Optional<UserPreferences> findByUser(User user); }
