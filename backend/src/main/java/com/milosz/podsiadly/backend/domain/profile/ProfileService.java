package com.milosz.podsiadly.backend.domain.profile;

import com.milosz.podsiadly.backend.domain.file.FileStorageService;
import com.milosz.podsiadly.backend.domain.profile.dto.ProfileDto;
import com.milosz.podsiadly.backend.domain.loginandregister.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository profiles;
    private final FileStorageService files;


    @Transactional
    public Profile createFor(User user) {
        return profiles.findByUserId(user.getId()).orElseGet(() -> {
            var p = Profile.builder()
                    .user(user)
                    .name(user.getUsername())
                    .email(null)
                    .build();
            return profiles.save(p);
        });
    }

    @Transactional(readOnly = true)
    public ProfileDto getDto(String userId) {
        var p = profiles.findByUserId(userId).orElseThrow();
        return ProfileMapper.toDto(p);
    }

    @Transactional
    public ProfileDto update(
            String userId,
            String name,
            String email,
            String about,
            LocalDate dob,
            String avatarPreset,
            MultipartFile avatar,
            MultipartFile cv
    ) {
        var p = profiles.findByUserId(userId).orElseThrow();

        if (name != null)  p.setName(name.trim());
        if (email != null) p.setEmail(email.trim());
        if (about != null)  p.setAbout(about.trim());
        if (dob != null)   p.setDob(dob);

        if (avatarPreset != null && !avatarPreset.isBlank()) {
            if (p.getAvatarFileId() != null) {
                files.deleteForOwner(p.getAvatarFileId(), userId);
                p.setAvatarFileId(null);
            }
            p.setAvatarPreset(avatarPreset);
            p.setAvatarUrl(avatarPreset);
        }

        if (avatar != null && !avatar.isEmpty()) {
            var saved = files.save(userId, avatar);
            if (p.getAvatarFileId() != null) {
                files.deleteForOwner(p.getAvatarFileId(), userId);
            }
            p.setAvatarPreset(null);
            p.setAvatarUrl(null);
            p.setAvatarFileId(saved.getId());
        }

        if (cv != null && !cv.isEmpty()) {
            var saved = files.save(userId, cv);
            if (p.getCvFileId() != null) {
                files.deleteForOwner(p.getCvFileId(), userId);
            }
            p.setCvFileId(saved.getId());
        }

        return ProfileMapper.toDto(p);
    }

    @Transactional
    public void clearAvatar(String userId) {
        var p = profiles.findByUserId(userId).orElseThrow();
        if (p.getAvatarFileId() != null) {
            files.deleteForOwner(p.getAvatarFileId(), userId);
        }
        p.setAvatarFileId(null);
        p.setAvatarPreset(null);
        p.setAvatarUrl(null);
    }

    @Transactional
    public void clearCv(String userId) {
        var p = profiles.findByUserId(userId).orElseThrow();
        if (p.getCvFileId() != null) {
            files.deleteForOwner(p.getCvFileId(), userId);
        }
        p.setCvFileId(null);
    }
}
