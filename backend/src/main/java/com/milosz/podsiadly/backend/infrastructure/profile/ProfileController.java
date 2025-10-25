package com.milosz.podsiadly.backend.infrastructure.profile;

import com.milosz.podsiadly.backend.domain.loginandregister.User;
import com.milosz.podsiadly.backend.domain.profile.ProfileService;
import com.milosz.podsiadly.backend.domain.profile.dto.ProfileDto;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService service;

    @GetMapping
    public ProfileDto me(@AuthenticationPrincipal User user) {
        return service.getDto(user.getId());
    }

    @PutMapping(
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ProfileDto update(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String about,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dob,
            @RequestParam(required = false) String avatarPreset,
            @RequestPart(required = false) MultipartFile avatar,
            @RequestPart(required = false) MultipartFile cv
    ) {
        return service.update(user.getId(), name, email, about, dob, avatarPreset, avatar, cv);
    }

    @DeleteMapping("/avatar")
    public ResponseEntity<Void> clearAvatar(@AuthenticationPrincipal User user) {
        service.clearAvatar(user.getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/cv")
    public ResponseEntity<Void> clearCv(@AuthenticationPrincipal User user) {
        service.clearCv(user.getId());
        return ResponseEntity.noContent().build();
    }
}
