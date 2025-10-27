package com.milosz.podsiadly.backend.infrastructure.profile;

import com.milosz.podsiadly.backend.domain.file.FileObject;
import com.milosz.podsiadly.backend.domain.file.FileStorageService;
import com.milosz.podsiadly.backend.domain.loginandregister.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile/file")
@RequiredArgsConstructor
public class ProfileFileController {

    private final FileStorageService files;

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> getOwn(@AuthenticationPrincipal User user, @PathVariable String id) {
        try {
            FileObject fo = files.getForOwner(id, user.getId());
            return ResponseEntity.ok()
                    .contentType(fo.getContentType() != null
                            ? MediaType.parseMediaType(fo.getContentType())
                            : MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.inline()
                                    .filename(fo.getFilename() != null ? fo.getFilename() : "file")
                                    .build().toString())
                    .cacheControl(CacheControl.noCache())
                    .body(fo.getData());
        } catch (SecurityException se) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadOwn(@AuthenticationPrincipal User user, @PathVariable String id) {
        try {
            var fo = files.getForOwner(id, user.getId());
            return ResponseEntity.ok()
                    .contentType(fo.getContentType() != null
                            ? MediaType.parseMediaType(fo.getContentType())
                            : MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment()
                                    .filename(fo.getFilename() != null ? fo.getFilename() : "file")
                                    .build().toString())
                    .cacheControl(CacheControl.noCache())
                    .body(fo.getData());
        } catch (SecurityException se) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
