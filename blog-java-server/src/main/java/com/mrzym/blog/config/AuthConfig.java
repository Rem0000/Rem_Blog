package com.mrzym.blog.config;

import com.mrzym.blog.auth.JwtUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthConfig {

    @Bean
    public JwtUtil jwtUtil(BlogProperties properties) {
        return new JwtUtil(properties.getJwtSecret());
    }
}
