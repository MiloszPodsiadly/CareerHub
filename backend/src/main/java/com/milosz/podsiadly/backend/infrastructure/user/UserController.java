package com.milosz.podsiadly.backend.infrastructure.user;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/user")
public class UserController {
    @GetMapping("/me")
    public Object me(Authentication auth) { return auth; }
}
