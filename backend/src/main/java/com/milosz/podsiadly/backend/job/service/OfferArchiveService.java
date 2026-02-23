package com.milosz.podsiadly.backend.job.service;

import com.milosz.podsiadly.backend.job.domain.ArchiveReason;
import com.milosz.podsiadly.backend.job.domain.JobOffer;
import com.milosz.podsiadly.backend.job.mapper.JobOfferHistoryMapper;
import com.milosz.podsiadly.backend.job.repository.JobOfferHistoryRepository;
import com.milosz.podsiadly.backend.job.repository.JobOfferOwnerRepository;
import com.milosz.podsiadly.backend.job.repository.JobOfferRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OfferArchiveService {

    private final JobOfferRepository offers;
    private final JobOfferHistoryRepository history;
    private final JobOfferHistoryMapper mapper;
    private final JobOfferOwnerRepository owners;

    @Transactional
    public int archiveInactiveOlderThan(Duration age, ArchiveReason reason) {
        Instant olderThan = Instant.now().minus(age);
        List<JobOffer> candidates = offers.findAllByActiveFalseAndLastSeenAtBefore(olderThan);
        int moved = 0;
        for (JobOffer o : candidates) {
            try {
                history.save(mapper.toHistory(o, reason));
            } catch (Exception e) {
                log.warn("[archive] history save failed for offerId={}", o.getId(), e);
            }
            owners.deleteByJobOffer_Id(o.getId());
            offers.delete(o);
            moved++;
        }
        if (moved > 0) log.info("[archive] moved {} inactive offers older than {}", moved, age);
        return moved;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void archiveSingle(JobOffer o, ArchiveReason reason) {
        JobOffer source = o;
        if (o != null && o.getId() != null) {
            source = offers.findById(o.getId()).orElse(o);
        }
        history.saveAndFlush(mapper.toHistory(source, reason));
    }
}
