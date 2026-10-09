package com.agentflow.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @GetMapping("/csrf") CsrfView csrf(CsrfToken token) {
        return new CsrfView(token.getToken(), token.getHeaderName(), token.getParameterName());
    }
    @GetMapping("/me") UserView me(Authentication auth) {
        return new UserView(auth.getName(), auth.getAuthorities().stream().map(Object::toString).toList());
    }
    public record CsrfView(String token, String headerName, String parameterName) {}
    public record UserView(String username, List<String> roles) {}
}
