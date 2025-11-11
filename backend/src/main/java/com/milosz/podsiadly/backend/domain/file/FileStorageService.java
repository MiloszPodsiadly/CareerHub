package com.milosz.podsiadly.backend.domain.file;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class FileStorageService {
    private final FileObjectRepository repo;

    @Transactional
    public FileObject save(String userId, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Empty file");
        try {
            var fo = FileObject.builder()
                    .userId(userId)
                    .filename(file.getOriginalFilename() != null ? file.getOriginalFilename() : "file")
                    .contentType(file.getContentType() != null ? file.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE)
                    .size(file.getSize())
                    .data(file.getBytes())
                    .build();
            return repo.save(fo);
        } catch (Exception e) {
            throw new RuntimeException("File save failed", e);
        }
    }

    @Transactional
    public FileObject saveRaw(String userId, String filename, String contentType, byte[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Empty file data");
        }
        var fo = FileObject.builder()
                .userId(userId)
                .filename((filename != null && !filename.isBlank()) ? filename : "file")
                .contentType((contentType != null && !contentType.isBlank())
                        ? contentType
                        : MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .size((long) data.length)
                .data(data)
                .build();
        return repo.save(fo);
    }

    @Transactional(readOnly = true)
    public FileObject getForOwner(String fileId, String userId) {
        var fo = repo.findById(fileId).orElseThrow();
        if (!fo.getUserId().equals(userId)) {
            throw new SecurityException("Forbidden");
        }
        return fo;
    }

    @Transactional
    public void deleteForOwner(String fileId, String userId) {
        var fo = repo.findById(fileId).orElse(null);
        if (fo != null && fo.getUserId().equals(userId)) {
            repo.delete(fo);
        }
    }
}
