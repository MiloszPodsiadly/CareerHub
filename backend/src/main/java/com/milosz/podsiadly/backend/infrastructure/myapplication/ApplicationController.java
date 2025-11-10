package com.milosz.podsiadly.backend.infrastructure.myapplication;

import com.milosz.podsiadly.backend.domain.file.FileStorageService;
import com.milosz.podsiadly.backend.domain.loginandregister.User;
import com.milosz.podsiadly.backend.domain.loginandregister.UserService;
import com.milosz.podsiadly.backend.domain.myapplication.ApplicationService;
import com.milosz.podsiadly.backend.domain.myapplication.ApplicationStatus;
import com.milosz.podsiadly.backend.domain.myapplication.JobApplicationRepository;
import com.milosz.podsiadly.backend.domain.myapplication.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
@Slf4j
@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
@CrossOrigin
public class ApplicationController {

    private final ApplicationService service;
    private final JobApplicationRepository apps;
    private final FileStorageService files;
    private final UserService userService;


    private String requireUserId(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        String username = auth.getName();
        if (username == null || username.isBlank() || "anonymousUser".equals(username)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        return userService.getByUsername(username).id();
    }

    private User toDomainUser(String userId) {
        User u = new User();
        u.setId(userId);
        return u;
    }


    @PostMapping
    public ResponseEntity<ApplicationDetailDto> apply(Authentication auth,
                                                      @RequestBody ApplicationCreateRequest req) {
        log.debug("POST /api/applications offerId={} authPrincipal={}",
                req.offerId(), auth != null ? auth.getName() : null);

        String uid = requireUserId(auth);
        ApplicationDetailDto dto = service.apply(toDomainUser(uid), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/mine")
    public List<ApplicationDto> mine(Authentication auth) {
        return service.mine(requireUserId(auth));
    }

    @GetMapping("/owned")
    public List<ApplicationDto> owned(Authentication auth) {
        return service.forOwned(requireUserId(auth));
    }

    @GetMapping("/{id}")
    public ApplicationDetailDto get(Authentication auth, @PathVariable Long id) {
        return service.getAccessible(id, requireUserId(auth));
    }

    @PatchMapping("/{id}/status")
    public ApplicationDetailDto setStatus(Authentication auth,
                                          @PathVariable Long id,
                                          @RequestBody StatusUpdateRequest req) {
        var st = ApplicationStatus.valueOf(req.status());
        return service.updateStatus(id, requireUserId(auth), st);
    }

    @GetMapping("/{id}/cv")
    public ResponseEntity<byte[]> downloadCv(Authentication auth, @PathVariable Long id) {
        String uid = requireUserId(auth);

        var app = apps.findAccessible(id, uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Application not found or forbidden"));

        if (app.getCvFileId() == null) return ResponseEntity.notFound().build();

        var fo = files.getForOwner(app.getCvFileId(), app.getApplicant().getId());
        return ResponseEntity.ok()
                .contentType(fo.getContentType() != null
                        ? MediaType.parseMediaType(fo.getContentType())
                        : MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fo.getFilename()).build().toString())
                .contentLength(fo.getSize())
                .body(fo.getData());
    }
}
