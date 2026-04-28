package com.connectsphere.auth.config;

import com.connectsphere.auth.dto.AuthDto.UserResponse;
import com.connectsphere.auth.entity.User;
import org.springframework.stereotype.Component;

/**
 * UserMapper — converts User entities to response DTOs.
 * Keeps sensitive fields (passwordHash) out of API responses.
 */
@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        if (user == null) return null;
        UserResponse r = new UserResponse();
        r.setUserId(user.getUserId());
        r.setUsername(user.getUsername());
        r.setEmail(user.getEmail());
        r.setFullName(user.getFullName());
        r.setBio(user.getBio());
        r.setProfilePicUrl(user.getProfilePicUrl());
        r.setRole(user.getRole());
        r.setProvider(user.getProvider());
        r.setIsActive(user.getIsActive());
        r.setCreatedAt(user.getCreatedAt());
        return r;
    }
}
