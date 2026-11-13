package com.grash.service;

import com.grash.dto.DateRange;
import com.grash.dto.ReadingHistogramDTO;
import com.grash.dto.ReadingPatchDTO;
import com.grash.dto.workOrder.WorkOrderPostDTO;
import com.grash.exception.CustomException;
import com.grash.mapper.ReadingMapper;
import com.grash.mapper.WorkOrderMapper;
import com.grash.model.Meter;
import com.grash.model.Notification;
import com.grash.model.Reading;
import com.grash.model.User;
import com.grash.model.WorkOrder;
import com.grash.model.WorkOrderMeterTrigger;
import com.grash.model.enums.NotificationType;
import com.grash.model.enums.PlanFeatures;
import com.grash.model.enums.WorkOrderMeterTriggerCondition;
import com.grash.model.enums.webhook.WebhookEvent;
import com.grash.repository.ReadingRepository;
import com.grash.utils.Helper;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReadingService {
    private final ReadingRepository readingRepository;
    private final jakarta.persistence.EntityManager entityManager;
    private final DomainCommandStore commandStore;
    private final MeterTriggerCommandService triggerCommands;
    private record ReadingResult(Long id, double value, Date createdAt, Date updatedAt, Long createdBy, Long updatedBy) {
        static ReadingResult from(Reading reading) { return new ReadingResult(reading.getId(),reading.getValue(),reading.getCreatedAt(),reading.getUpdatedAt(),reading.getCreatedBy(),reading.getUpdatedBy()); }
        Reading toReading() { Reading r = new Reading(); r.setId(id); r.setValue(value); r.setCreatedAt(createdAt); r.setUpdatedAt(updatedAt); r.setCreatedBy(createdBy); r.setUpdatedBy(updatedBy); return r; }
    }
    private final ReadingMapper readingMapper;
    private MeterService meterService;

    @Autowired
    public void setDeps(@Lazy MeterService meterService
    ) {
        this.meterService = meterService;
    }

    @Transactional
    public Reading create(Reading request, User user) { return create(request, user, null); }

    @Transactional
    public Reading create(Reading request, User user, String commandKey) {
        if (!Double.isFinite(request.getValue()) || request.getMeter() == null || request.getMeter().getId() == null)
            throw new CustomException("A meter and a finite reading are required", HttpStatus.BAD_REQUEST);
        if (!user.getCompany().getSubscription().getSubscriptionPlan().getFeatures().contains(PlanFeatures.METER))
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        Long meterId = request.getMeter().getId();
        String payload = "create:" + meterId + ":" + request.getValue();
        Optional<DomainCommandStore.Stored> previous = commandStore.begin("READING",commandKey,user,payload);
        Meter meter = entityManager.find(Meter.class,meterId,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (meter == null) throw new CustomException("Meter not found",HttpStatus.NOT_FOUND);
        entityManager.refresh(meter,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (!Objects.equals(meter.getCompany().getId(),user.getCompany().getId()) || !meter.canBeViewedBy(user))
            throw new CustomException("Access denied",HttpStatus.FORBIDDEN);
        if (previous.isPresent()) return commandStore.result(previous.get(),new com.fasterxml.jackson.core.type.TypeReference<ReadingResult>() {}).toReading();
        Optional<Reading> last = findLastByMeter(meterId);
        if (last.isPresent()) {
            String timeZone = meter.getCompany().getCompanySettings().getGeneralPreferences().getTimeZone();
            LocalDate next = last.get().getCreatedAt().toInstant().atZone(ZoneId.of(timeZone)).toLocalDate().plusDays(meter.getUpdateFrequency());
            if (LocalDate.now(ZoneId.of(timeZone)).isBefore(next))
                throw new CustomException("The update frequency has not been respected",HttpStatus.NOT_ACCEPTABLE);
        }
        Reading saved = new Reading();
        saved.setMeter(meter);
        saved.setValue(request.getValue());
        saved = readingRepository.saveAndFlush(saved); // source ID and reading must exist before its work order
        triggerCommands.process(meter,saved,user);
        commandStore.complete("READING",commandKey,user,payload,"METER",meterId,ReadingResult.from(saved));
        return saved;
    }

    public Collection<Reading> getAll() {
        return readingRepository.findAll();
    }

    public Collection<Reading> getByMeter(Long id, User user) {
        Optional<Meter> optionalMeter = meterService.findById(id);
        if (optionalMeter.isPresent()) {
            if (!optionalMeter.get().canBeViewedBy(user))
                throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
            return readingRepository.findByMeter_Id(id);
        } else throw new CustomException("Not found", HttpStatus.NOT_FOUND);
    }

    public List<ReadingHistogramDTO> getHistogram(Long id, DateRange dateRange, User user) {
        Optional<Meter> optionalMeter = meterService.findById(id);
        if (optionalMeter.isEmpty()) {
            throw new CustomException("Meter not found", HttpStatus.NOT_FOUND);
        }
        if (!optionalMeter.get().canBeViewedBy(user))
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        if (dateRange.getStart() == null || dateRange.getEnd() == null) {
            throw new CustomException("Start and end dates are required", HttpStatus.BAD_REQUEST);
        }
        if (dateRange.getStart().after(dateRange.getEnd())) {
            throw new CustomException("Start date must be before end date", HttpStatus.BAD_REQUEST);
        }
        return getHistogramData(id, dateRange.getStart(),
                dateRange.getEnd(), user.getCompany().getCompanySettings().getGeneralPreferences().getTimeZone());
    }

    @Transactional
    public Reading patch(Long id, ReadingPatchDTO reading, User user) {
        Optional<Reading> optionalReading = readingRepository.findById(id);

        if (optionalReading.isPresent()) {
            Reading savedReading = optionalReading.get();
            if (!savedReading.getMeter().canBeViewedBy(user))
                throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
            if (!Double.isFinite(reading.getValue())) throw new CustomException("Reading must be finite", HttpStatus.BAD_REQUEST);
            entityManager.lock(savedReading.getMeter(), jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            if (!Objects.equals(savedReading.getMeter().getCompany().getId(),user.getCompany().getId())) throw new CustomException("Access denied",HttpStatus.FORBIDDEN);
            Reading updated = readingRepository.save(readingMapper.updateReading(savedReading, reading));
            // Editing history does not open, close or rewind the currently accepted anomaly episode.
            return updated;
        } else throw new CustomException("Reading not found", HttpStatus.NOT_FOUND);
    }

    @Transactional
    public void deleteByIdAndUser(Long id, User user) {
        Optional<Reading> optionalReading = readingRepository.findById(id);

        if (optionalReading.isPresent()) {
            if (!optionalReading.get().getMeter().canBeViewedBy(user))
                throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
            entityManager.lock(optionalReading.get().getMeter(), jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            if (!Objects.equals(optionalReading.get().getMeter().getCompany().getId(),user.getCompany().getId())) throw new CustomException("Access denied",HttpStatus.FORBIDDEN);
            // Deleting even the latest sample preserves real-time state; a NEW reading establishes recovery.
            readingRepository.deleteById(id);
        } else throw new CustomException("Reading not found", HttpStatus.NOT_FOUND);
    }

    public Optional<Reading> findById(Long id) {
        return readingRepository.findById(id);
    }

    public Collection<Reading> findByCompany(Long id) {
        return readingRepository.findByCompany_Id(id);
    }

    public Optional<Reading> findLastByMeter(Long id) {
        return readingRepository.findFirstByMeter_IdOrderByCreatedAtDesc(id);
    }

    public List<ReadingHistogramDTO> getHistogramData(Long meterId, Date start, Date end, @NotNull String timeZone) {
        Collection<Reading> readings = readingRepository.findByMeter_IdAndCreatedAtBetween(meterId, start, end);
        if (readings.isEmpty()) {
            return Collections.emptyList();
        }

        long totalDays = TimeUnit.MILLISECONDS.toDays(end.getTime() - start.getTime()) + 1;
        int maxPoints = 30;

        List<Reading> sorted = readings.stream()
                .sorted(Comparator.comparing(Reading::getCreatedAt))
                .toList();

        int bucketSize = (int) Math.max(1, Math.ceil((double) totalDays / maxPoints));

        Calendar cal = Calendar.getInstance();
        cal.setTime(start);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.setTimeZone(TimeZone.getTimeZone(timeZone));

        List<ReadingHistogramDTO> result = new ArrayList<>();
        Date bucketStart = cal.getTime();

        while (!bucketStart.after(end)) {
            cal.setTime(bucketStart);
            cal.add(Calendar.DAY_OF_MONTH, bucketSize);
            cal.add(Calendar.MILLISECOND, -1);
            Date bucketEnd = cal.getTime();
            if (bucketEnd.after(end)) {
                bucketEnd = end;
            }

            final Date bStart = bucketStart;
            final Date bEnd = bucketEnd;
            List<Reading> bucket = sorted.stream()
                    .filter(r -> !r.getCreatedAt().before(bStart) && !r.getCreatedAt().after(bEnd))
                    .toList();

            if (!bucket.isEmpty()) {
                double avg = bucket.stream().mapToDouble(Reading::getValue).average().orElse(0);
                Date midpoint = new Date((bStart.getTime() + bEnd.getTime()) / 2);
                result.add(ReadingHistogramDTO.builder()
                        .date(midpoint)
                        .value(Math.round(avg * 100.0) / 100.0)
                        .count(bucket.size())
                        .build());
            }

            cal.setTime(bucketStart);
            cal.add(Calendar.DAY_OF_MONTH, bucketSize);
            bucketStart = cal.getTime();
        }

        return result;
    }
}