package com.milosz.podsiadly.backend.job.service;

import com.milosz.podsiadly.backend.job.domain.ContractType;
import com.milosz.podsiadly.backend.job.domain.JobLevel;
import com.milosz.podsiadly.backend.job.domain.JobOffer;
import com.milosz.podsiadly.backend.job.domain.JobOfferOwner;
import com.milosz.podsiadly.backend.job.dto.JobOfferDetailDto;
import com.milosz.podsiadly.backend.job.dto.JobOfferListDto;
import com.milosz.podsiadly.backend.job.mapper.JobOfferMapper;
import com.milosz.podsiadly.backend.job.repository.JobOfferOwnerRepository;
import com.milosz.podsiadly.backend.job.repository.JobOfferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class JobOfferService {

    private final JobOfferRepository repo;

    private static final Map<String, List<String>> SPEC_TO_TECH = Map.ofEntries(
            e("frontend",  List.of("React","Angular","Vue","JavaScript","TypeScript","HTML","CSS","Next.js","Nuxt")),
            e("backend",   List.of("Java","Spring","Kotlin","Node.js","NestJS","Python","Django","FastAPI",".NET","C#","Go","PHP","Laravel")),
            e("fullstack", List.of("React","Angular","Vue","JavaScript","TypeScript","Node.js","Java",".NET","Python")),
            e("mobile",    List.of("Android","iOS","Swift","Kotlin","Flutter","React Native")),
            e("devops",    List.of("Docker","Kubernetes","K8s","Terraform","AWS","GCP","Azure","CI/CD","Helm","Ansible","Prometheus","Grafana")),
            e("qa",        List.of("QA","Test","Testing","Cypress","Playwright","Selenium","JUnit","PyTest")),
            e("data",      List.of("SQL","Python","Spark","Hadoop","dbt","Airflow","Kafka","Snowflake","BigQuery","PowerBI")),
            e("security",  List.of("Security","AppSec","Pentest","SIEM","SOC","OWASP","IAM")),
            e("embedded",  List.of("Embedded","C","C++","RTOS","STM32","ARM","IoT")),
            e("ai/ml",     List.of("ML","Machine Learning","AI","TensorFlow","PyTorch","LangChain","OpenAI")),
            e("others",    List.of())
    );
    private static Map.Entry<String,List<String>> e(String k, List<String> v) {
        return new AbstractMap.SimpleEntry<>(k.toLowerCase(Locale.ROOT), v);
    }
    private static List<String> expandSpecs(List<String> spec) {
        if (spec == null || spec.isEmpty()) return List.of();
        return spec.stream()
                .filter(Objects::nonNull)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .flatMap(s -> SPEC_TO_TECH.getOrDefault(s, List.of()).stream())
                .distinct()
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<JobOfferListDto> search(
            String q, String city, Boolean remote, JobLevel level,
            List<String> spec, List<String> tech,
            Integer salaryMin, Integer salaryMax, Instant postedAfter,
            Set<ContractType> contracts, Boolean withSalary,
            Pageable pageable
    ) {
        List<String> allTech = Stream.concat(
                tech == null ? Stream.empty() : tech.stream(),
                expandSpecs(spec).stream()
        ).distinct().collect(Collectors.toList());

        Specification<JobOffer> sp = Specification.allOf(
                JobOfferSpecifications.active(),
                JobOfferSpecifications.text(q),
                JobOfferSpecifications.byCity(city),
                JobOfferSpecifications.remote(remote),
                JobOfferSpecifications.level(level),
                JobOfferSpecifications.techAny(allTech),
                JobOfferSpecifications.salaryBetween(salaryMin, salaryMax),
                JobOfferSpecifications.postedAfter(postedAfter),
                JobOfferSpecifications.contractAny(contracts),
                JobOfferSpecifications.withSalary(withSalary)
        );

        return repo.findAll(sp, pageable).map(JobOfferMapper::toListDto);
    }

    @Transactional(readOnly = true)
    public List<JobOfferListDto> searchAll(
            String q, String city, Boolean remote, JobLevel level,
            List<String> spec, List<String> tech,
            Integer salaryMin, Integer salaryMax, Instant postedAfter,
            Set<ContractType> contracts, Boolean withSalary,
            Sort sort
    ) {
        List<String> allTech = Stream.concat(
                tech == null ? Stream.empty() : tech.stream(),
                expandSpecs(spec).stream()
        ).distinct().collect(Collectors.toList());

        Specification<JobOffer> sp = Specification.allOf(
                JobOfferSpecifications.active(),
                JobOfferSpecifications.text(q),
                JobOfferSpecifications.byCity(city),
                JobOfferSpecifications.remote(remote),
                JobOfferSpecifications.level(level),
                JobOfferSpecifications.techAny(allTech),
                JobOfferSpecifications.salaryBetween(salaryMin, salaryMax),
                JobOfferSpecifications.postedAfter(postedAfter),
                JobOfferSpecifications.contractAny(contracts),
                JobOfferSpecifications.withSalary(withSalary)
        );

        return repo.findAll(sp, sort).stream()
                .map(JobOfferMapper::toListDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public JobOfferDetailDto get(Long id) {
        return repo.findById(id)
                .map(JobOfferMapper::toDetailDto)
                .orElseThrow(() -> new IllegalArgumentException("Offer not found: " + id));
    }
    @Transactional(readOnly = true)
    public List<JobOfferListDto> listOwned(String userId) {
        return repo.findOwnedByUserId(userId).stream()
                .map(JobOfferMapper::toListDto)
                .toList();
    }

    // Jeśli chcesz wyświetlać tylko „platformowe”:
    @Transactional(readOnly = true)
    public List<JobOfferListDto> listOwnedPlatformOnly(String userId) {
        return repo.findPlatformOwnedByUserId(userId).stream()
                .map(JobOfferMapper::toListDto)
                .toList();
    }


}
