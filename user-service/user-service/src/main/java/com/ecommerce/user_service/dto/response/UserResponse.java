package com.ecommerce.user_service.dto.response;
import com.ecommerce.user_service.entity.User; import java.time.*; import java.util.*;
public record UserResponse(Long id,String name,String email,String phone,String gender,LocalDate dob,String profileImage,Instant memberSince,Set<String> roles){ public static UserResponse from(User u){return new UserResponse(u.getId(),u.getName(),u.getEmail(),u.getPhone(),u.getGender(),u.getDob(),u.getProfileImage(),u.getCreatedDate(),u.getRoles().stream().map(r->r.getName().name()).collect(java.util.stream.Collectors.toSet()));} }
