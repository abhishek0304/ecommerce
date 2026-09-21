package com.ecommerce.user_service.repository;
import com.ecommerce.user_service.entity.*; import org.springframework.data.jpa.repository.*; import java.util.*;
public interface AddressRepository extends JpaRepository<Address,Long>{ List<Address> findByUserOrderByDefaultAddressDescIdDesc(User user); @Modifying @Query("update Address a set a.defaultAddress=false where a.user=:user") void clearDefault(User user); }
