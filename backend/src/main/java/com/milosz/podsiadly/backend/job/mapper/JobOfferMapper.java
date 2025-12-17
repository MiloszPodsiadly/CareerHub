package com.milosz.podsiadly.backend.job.mapper;

import com.milosz.podsiadly.backend.job.domain.*;
import com.milosz.podsiadly.backend.job.dto.*;

import java.util.*;

public final class JobOfferMapper {

    private JobOfferMapper() {}

    public static JobOfferListDto toListDto(JobOffer e) {
        return new JobOfferListDto(
                e.getId(),
                e.getTitle(),
                e.getCompany() != null ? e.getCompany().getName() : null,
                e.getCity() != null ? e.getCity().getName() : null,
                e.getRemote(),
                e.getLevel() != null ? e.getLevel().name() : null,
                e.getContract() != null ? e.getContract().name() : null,
                toNames(e.getContracts()),
                e.getSalaryMin(),
                e.getSalaryMax(),
                e.getCurrency(),
                safeTags(e.getTechTags()),
                e.getPublishedAt()
        );
    }

    public static JobOfferDetailDto toDetailDto(JobOffer e) {
        return new JobOfferDetailDto(
                e.getId(),
                e.getSource() != null ? e.getSource().name() : null,
                e.getExternalId(),
                e.getUrl(),
                e.getApplyUrl(),
                e.getTitle(),
                e.getDescription(),
                e.getCompany() != null ? e.getCompany().getName() : null,
                e.getCity() != null ? e.getCity().getName() : null,
                e.getRemote(),
                e.getLevel() != null ? e.getLevel().name() : null,
                e.getContract() != null ? e.getContract().name() : null,
                toNames(e.getContracts()),
                e.getSalaryMin(),
                e.getSalaryMax(),
                e.getCurrency(),
                safeTags(e.getTechTags()),
                toSkillDtos(e.getTechStack()),
                e.getPublishedAt(),
                e.getActive()
        );
    }

    private static List<String> toNames(Set<ContractType> set) {
        if (set == null || set.isEmpty()) return List.of();
        return set.stream().filter(Objects::nonNull).map(Enum::name).sorted().toList();
    }

    private static List<String> safeTags(List<String> tags) {
        if (tags == null) return List.of();
        return new ArrayList<>(tags);
    }

    private static List<JobOfferSkillDto> toSkillDtos(List<JobOfferSkill> list) {
        if (list == null) return List.of();
        List<JobOfferSkillDto> out = new ArrayList<>(list.size());
        for (JobOfferSkill s : list) {
            out.add(new JobOfferSkillDto(
                    s.getName(),
                    s.getLevelLabel(),
                    s.getLevelValue(),
                    s.getSource()
            ));
        }
        return out;
    }

    public static void applySkills(JobOffer target, List<JobOfferSkillDto> dtos) {
        if (target.getTechStack() == null) {
            target.setTechStackIfNull();
        }

        target.getTechStack().clear();

        if (dtos == null || dtos.isEmpty()) return;

        for (JobOfferSkillDto d : dtos) {
            if (d == null) continue;

            JobOfferSkill skill = JobOfferSkill.builder()
                    .name(d.name())
                    .levelLabel(d.levelLabel())
                    .levelValue(d.levelValue())
                    .source(d.source())
                    .build();

            skill.setJobOffer(target);

            target.getTechStack().add(skill);
        }
    }
}
