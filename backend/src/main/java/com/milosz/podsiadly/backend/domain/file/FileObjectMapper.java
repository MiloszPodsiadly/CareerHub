package com.milosz.podsiadly.backend.domain.file;

import com.milosz.podsiadly.backend.domain.file.dto.FileObjectDto;

public final class FileObjectMapper {
    private FileObjectMapper() {}

    public static FileObjectDto toDto(FileObject f) {
        if (f == null) return null;
        return new FileObjectDto(
                f.getId(),
                f.getUserId(),
                f.getFilename(),
                f.getContentType(),
                f.getSize()
        );
    }
}