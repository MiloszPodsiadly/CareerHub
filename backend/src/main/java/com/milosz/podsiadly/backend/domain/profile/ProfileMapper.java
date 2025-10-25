package com.milosz.podsiadly.backend.domain.profile;

import com.milosz.podsiadly.backend.domain.profile.dto.ProfileDto;

final class ProfileMapper {
    static ProfileDto toDto(Profile p) {
        return new ProfileDto(
                p.getUserId(),
                p.getName(),
                p.getEmail(),
                p.getAbout(),
                p.getDob(),
                p.getAvatarUrl(),
                p.getAvatarPreset(),
                p.getAvatarFileId(),
                p.getCvFileId()
        );
    }
}
