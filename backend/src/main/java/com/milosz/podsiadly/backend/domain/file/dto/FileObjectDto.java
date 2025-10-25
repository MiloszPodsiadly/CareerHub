package com.milosz.podsiadly.backend.domain.file.dto;

public record FileObjectDto(
        String id,
        String userId,
        String filename,
        String contentType,
        long size
) {}
