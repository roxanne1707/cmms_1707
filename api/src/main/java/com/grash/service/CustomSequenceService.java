package com.grash.service;

import com.grash.model.Company;
import com.grash.model.CustomSequence;
import com.grash.repository.CustomSequenceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;

@Service
@RequiredArgsConstructor
public class CustomSequenceService {
    private final CustomSequenceRepository customSequenceRepository;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbc;

    public CustomSequence findByCompanyId(Long companyId) {
        return customSequenceRepository.findByCompanyId(companyId)
                .orElse(null);
    }

    @Transactional
    public CustomSequence createCustomSequence(Company company) {
        return getOrCreateCustomSequence(company);
    }

    @Transactional
    public CustomSequence getOrCreateCustomSequence(Company company) {
        if (company == null || company.getId() == null) {
            throw new IllegalArgumentException("A persisted company is required for sequence allocation");
        }
        // Serialize initialization and allocation until the caller's transaction commits. A company row
        // FOR UPDATE lock would conflict with FK KEY SHARE locks already taken by concurrent commands.
        long lockKey = Long.parseUnsignedLong(
                DomainCommandStore.hash("CUSTOM_SEQUENCE:" + company.getId()).substring(0, 16), 16);
        jdbc.queryForList("SELECT pg_advisory_xact_lock(?)", lockKey);
        CustomSequence customSequence = findByCompanyId(company.getId());
        if (customSequence == null) {
            return customSequenceRepository.saveAndFlush(new CustomSequence(company));
        }
        // The query can return an older entity from this persistence context; the lock alone does not refresh it.
        entityManager.refresh(customSequence, LockModeType.PESSIMISTIC_WRITE);
        return customSequence;
    }

    @Transactional
    public Long getNextWorkOrderSequence(Company company) {
        CustomSequence customSequence = getOrCreateCustomSequence(company);
        Long nextSequence = customSequence.getAndIncrementWorkOrderSequence();
        customSequenceRepository.save(customSequence);
        return nextSequence;
    }


    @Transactional
    public Long getNextAssetSequence(Company company) {
        CustomSequence customSequence = getOrCreateCustomSequence(company);
        Long nextSequence = customSequence.getAndIncrementAssetSequence();
        customSequenceRepository.save(customSequence);
        return nextSequence;
    }

    @Transactional
    public Long getNextPreventiveMaintenanceSequence(Company company) {
        CustomSequence customSequence = getOrCreateCustomSequence(company);
        Long nextSequence = customSequence.getAndIncrementPreventiveMaintenanceSequence();
        customSequenceRepository.save(customSequence);
        return nextSequence;
    }

    @Transactional
    public Long getNextLocationSequence(Company company) {
        CustomSequence customSequence = getOrCreateCustomSequence(company);
        Long nextSequence = customSequence.getAndIncrementLocationSequence();
        customSequenceRepository.save(customSequence);
        return nextSequence;
    }

    @Transactional
    public Long getNextRequestSequence(Company company) {
        CustomSequence customSequence = getOrCreateCustomSequence(company);
        Long nextSequence = customSequence.getAndIncrementRequestSequence();
        customSequenceRepository.save(customSequence);
        return nextSequence;
    }

}
