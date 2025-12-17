package com.milosz.podsiadly.backend.domain.myapplication;

import com.milosz.podsiadly.backend.domain.file.FileStorageService;
import com.milosz.podsiadly.backend.domain.loginandregister.User;
import com.milosz.podsiadly.backend.domain.profile.ProfileRepository;
import com.milosz.podsiadly.backend.domain.myapplication.dto.ApplicationCreateRequest;
import com.milosz.podsiadly.backend.domain.myapplication.dto.ApplicationDetailDto;
import com.milosz.podsiadly.backend.domain.myapplication.dto.ApplicationDto;
import com.milosz.podsiadly.backend.job.domain.JobOfferOwner;
import com.milosz.podsiadly.backend.job.repository.JobOfferOwnerRepository;
import com.milosz.podsiadly.backend.job.repository.JobOfferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final JobApplicationRepository apps;
    private final JobOfferRepository offers;
    private final JobOfferOwnerRepository owners;
    private final ProfileRepository profiles;
    private final FileStorageService files;

    @Transactional
    public ApplicationDetailDto apply(User user, ApplicationCreateRequest req) {
        var offer = offers.findById(req.offerId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Offer not found: " + req.offerId()
                ));

        if (apps.existsByApplicant_IdAndOffer_Id(user.getId(), offer.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already applied to this offer.");
        }

        var profile = profiles.findByUserId(user.getId()).orElse(null);
        String profileCvId = profile != null ? profile.getCvFileId() : null;

        String snapshotCvId = null;
        if (profileCvId != null && !profileCvId.isBlank()) {
            try {
                var src = files.getForOwner(profileCvId, user.getId());
                String fname = "cv-application-offer-" + offer.getId() + "-" +
                        (src.getFilename() != null ? src.getFilename() : "cv.pdf");

                snapshotCvId = files.saveRaw(
                        user.getId(),
                        fname,
                        src.getContentType(),
                        src.getData()
                ).getId();
            } catch (Exception ignore) {
                snapshotCvId = null;
            }
        }

        String offerTitleSnap = offer.getTitle();
        String offerCompanySnap = offer.getCompany() != null ? offer.getCompany().getName() : null;
        String offerCitySnap = offer.getCity() != null ? offer.getCity().getName() : null;

        String applyUrl = offer.getApplyUrl() != null ? offer.getApplyUrl() : offer.getUrl();

        String ownerIdSnap = owners.findByJobOffer_Id(offer.getId())
                .map(JobOfferOwner::getUser)
                .map(User::getId)
                .orElse(null);

        var app = JobApplication.builder()
                .offer(offer)
                .applicant(user)
                .cvFileId(snapshotCvId)
                .applyUrl(applyUrl)
                .note(req.note())
                .status(ApplicationStatus.APPLIED)
                .offerTitleSnapshot(offerTitleSnap)
                .offerCompanySnapshot(offerCompanySnap)
                .offerCitySnapshot(offerCitySnap)
                .offerApplyUrlSnapshot(applyUrl)
                .offerOwnerIdSnapshot(ownerIdSnap)
                .build();

        try {
            app = apps.saveAndFlush(app);
        } catch (org.springframework.dao.DataIntegrityViolationException |
                 org.hibernate.exception.ConstraintViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already applied to this offer.", e);
        } catch (jakarta.persistence.PersistenceException e) {
            if (e.getCause() instanceof org.hibernate.exception.ConstraintViolationException) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already applied to this offer.", e);
            }
            throw e;
        }

        return toDetailDto(app);
    }

    @Transactional(readOnly = true)
    public List<ApplicationDto> mine(String userId) {
        return apps.findByApplicant_IdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toListDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ApplicationDto> forOwned(String ownerId) {
        return apps.findForOwnedOffers(ownerId)
                .stream()
                .map(this::toListDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ApplicationDetailDto getAccessible(Long id, String userId) {
        var a = apps.findAccessible(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found or forbidden."));
        return toDetailDto(a);
    }

    @Transactional
    public ApplicationDetailDto updateStatus(Long id, String userId, ApplicationStatus newStatus) {
        var a = apps.findAccessible(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found or forbidden."));
        a.setStatus(newStatus);
        return toDetailDto(a);
    }

    private ApplicationDto toListDto(JobApplication a) {
        var o = a.getOffer();

        Long offerId = (o != null) ? o.getId() : null;

        String title = (o != null && o.getTitle() != null)
                ? o.getTitle()
                : (a.getOfferTitleSnapshot() != null ? a.getOfferTitleSnapshot() : "(Offer removed)");

        String company = (o != null && o.getCompany() != null)
                ? o.getCompany().getName()
                : a.getOfferCompanySnapshot();

        String city = (o != null && o.getCity() != null)
                ? o.getCity().getName()
                : a.getOfferCitySnapshot();

        String cvUrl = a.getCvFileId() != null ? ("/api/applications/" + a.getId() + "/cv") : null;

        return new ApplicationDto(
                a.getId(),
                offerId,
                title,
                company,
                city,
                a.getStatus() != null ? a.getStatus().name() : null,
                cvUrl,
                a.getCreatedAt()
        );
    }

    private ApplicationDetailDto toDetailDto(JobApplication a) {
        var o = a.getOffer();

        Long offerId = (o != null) ? o.getId() : null;

        String title = (o != null && o.getTitle() != null)
                ? o.getTitle()
                : (a.getOfferTitleSnapshot() != null ? a.getOfferTitleSnapshot() : "(Offer removed)");

        String ownerName = (o != null)
                ? owners.findByJobOffer_Id(o.getId())
                .map(JobOfferOwner::getUser)
                .map(User::getUsername)
                .orElse(null)
                : null;

        var applicant = a.getApplicant();
        var applicantProfile = profiles.findByUserId(applicant.getId()).orElse(null);
        String applicantEmail = applicantProfile != null ? applicantProfile.getEmail() : null;

        String applyUrl = (a.getApplyUrl() != null && !a.getApplyUrl().isBlank())
                ? a.getApplyUrl()
                : a.getOfferApplyUrlSnapshot();

        String cvUrl = a.getCvFileId() != null ? ("/api/applications/" + a.getId() + "/cv") : null;

        return new ApplicationDetailDto(
                a.getId(),
                offerId,
                title,
                ownerName,
                applicant.getUsername(),
                applicantEmail,
                a.getNote(),
                a.getStatus() != null ? a.getStatus().name() : null,
                applyUrl,
                cvUrl,
                a.getCreatedAt()
        );
    }
}
