package com.milosz.podsiadly.backend.infrastructure.user;

import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/admin")
public class AdminController {
    @GetMapping("/ping")
    public String ping() { return "admin-ok"; }
}
