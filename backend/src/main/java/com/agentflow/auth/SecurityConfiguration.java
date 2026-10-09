package com.agentflow.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean UserDetailsService userDetailsService(UserRepository users) {
        return username -> {
            var user = users.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
            return User.withUsername(user.getUsername()).password(user.getPasswordHash()).roles(user.getRole()).build();
        };
    }

    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        // Session authentication retains Spring's CSRF protection and fixation protection.
        return http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/csrf", "/actuator/health", "/error").permitAll()
                        .anyRequest().authenticated())
                .requestCache(cache -> cache.disable())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, failure) -> response.sendError(401))
                        .accessDeniedHandler((request, response, failure) -> response.sendError(403)))
                .formLogin(login -> login.loginProcessingUrl("/api/auth/login")
                        .successHandler((request, response, auth) -> response.setStatus(204))
                        .failureHandler((request, response, failure) -> response.sendError(401))
                        .permitAll())
                .logout(logout -> logout.logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((request, response, auth) -> response.setStatus(204)))
                .build();
    }

    @Bean ApplicationRunner bootstrapUser(UserRepository users, PasswordEncoder passwords,
            @Value("${agentflow.bootstrap.username}") String username,
            @Value("${agentflow.bootstrap.password}") String password) {
        if (!username.matches("[a-zA-Z0-9_.-]{3,100}") || password.length() < 12 || password.length() > 72) {
            throw new IllegalStateException("Bootstrap requires a valid username and a password of 12–72 characters");
        }
        return args -> {
            if (users.findByUsername(username).isEmpty()) users.save(new AppUser(username, passwords.encode(password)));
        };
    }
}
