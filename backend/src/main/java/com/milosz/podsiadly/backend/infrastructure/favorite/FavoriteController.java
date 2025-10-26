package com.milosz.podsiadly.backend.infrastructure.favorite;

import com.milosz.podsiadly.backend.domain.favorite.FavoriteService;
import com.milosz.podsiadly.backend.domain.favorite.FavoriteType;
import com.milosz.podsiadly.backend.domain.favorite.dto.FavoriteDto;
import com.milosz.podsiadly.backend.domain.favorite.dto.FavoritePageDto;
import com.milosz.podsiadly.backend.domain.favorite.dto.FavoriteStatusDto;
import com.milosz.podsiadly.backend.domain.loginandregister.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService service;
    private final UserService userService;

    private @Nullable String userIdOrNull(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()
                || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            return null;
        }
        var username = auth.getName();
        if (username == null || username.isBlank() || "anonymousUser".equals(username)) return null;
        return userService.getByUsername(username).id();
    }

    private String requireUserId(Authentication auth) {
        var uid = userIdOrNull(auth);
        if (uid == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        return uid;
    }

    @PutMapping("/{type}/{id}")
    @PreAuthorize("!isAnonymous()")
    public ResponseEntity<Void> add(Authentication auth,
                                    @PathVariable FavoriteType type,
                                    @PathVariable("id") Long targetId) {
        service.add(requireUserId(auth), type, targetId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{type}/{id}")
    @PreAuthorize("!isAnonymous()")
    public ResponseEntity<Void> remove(Authentication auth,
                                       @PathVariable FavoriteType type,
                                       @PathVariable("id") Long targetId) {
        service.remove(requireUserId(auth), type, targetId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{type}/{id}/toggle")
    @PreAuthorize("!isAnonymous()")
    public FavoriteStatusDto toggle(Authentication auth,
                                    @PathVariable FavoriteType type,
                                    @PathVariable("id") Long targetId) {
        var uid = requireUserId(auth);
        var fav = service.toggle(uid, type, targetId);
        var st  = service.status(uid, type, targetId);
        return new FavoriteStatusDto(fav, st.count());
    }

    @GetMapping("/{type}/{id}/status")
    public FavoriteStatusDto status(Authentication auth,
                                    @PathVariable FavoriteType type,
                                    @PathVariable("id") Long targetId) {
        return service.status(userIdOrNull(auth), type, targetId);
    }

    @GetMapping("/mine")
    @PreAuthorize("!isAnonymous()")
    public FavoritePageDto<FavoriteDto> mine(Authentication auth,
                                             @RequestParam FavoriteType type,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return service.listMine(requireUserId(auth), type, page, size);
    }
}
