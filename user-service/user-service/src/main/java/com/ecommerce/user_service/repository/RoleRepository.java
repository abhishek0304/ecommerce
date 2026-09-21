package com.ecommerce.user_service.repository;
import com.ecommerce.user_service.entity.*; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface RoleRepository extends JpaRepository<Role,Long>{ Optional<Role> findByName(RoleName name); }
