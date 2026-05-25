package com.example.hrms.service;

import com.example.hrms.dto.ActiveWorkerResponse;
import com.example.hrms.dto.AttendanceResponse;
import com.example.hrms.dto.PagedResponse;
import com.example.hrms.entity.AttendanceLog;
import com.example.hrms.entity.OvertimeEntry;
import com.example.hrms.entity.Site;
import com.example.hrms.entity.Worker;
import com.example.hrms.exception.ApiException;
import com.example.hrms.exception.ErrorCode;
import com.example.hrms.repository.AttendanceLogRepository;
import com.example.hrms.repository.OvertimeEntryRepository;
import com.example.hrms.repository.SiteRepository;
import com.example.hrms.repository.WorkerRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class AttendanceService {

    private static final String ACTIVE_WORKERS_KEY = "active:workers";

    private final WorkerRepository workerRepository;
    private final SiteRepository siteRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final OvertimeEntryRepository overtimeEntryRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.overtime.standard-shift-hours:8}")
    private int standardShiftHours;
    @Value("${app.overtime.rate-tier1-multiplier:1.5}")
    private double tier1Multiplier;
    @Value("${app.overtime.rate-tier1-hours:2}")
    private int tier1Hours;
    @Value("${app.overtime.rate-tier2-multiplier:2.0}")
    private double tier2Multiplier;
    @Value("${app.overtime.monthly-cap-hours:60}")
    private int monthlyCapHours;
    @Value("${app.overtime.max-shift-hours:16}")
    private int maxShiftHours;
    @Value("${app.redis.active-workers-ttl-hours:16}")
    private int activeTtlHours;

    public AttendanceService(WorkerRepository workerRepository,
                             SiteRepository siteRepository,
                             AttendanceLogRepository attendanceLogRepository,
                             OvertimeEntryRepository overtimeEntryRepository,
                             RedisTemplate<String, Object> redisTemplate) {
        this.workerRepository = workerRepository;
        this.siteRepository = siteRepository;
        this.attendanceLogRepository = attendanceLogRepository;
        this.overtimeEntryRepository = overtimeEntryRepository;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public AttendanceResponse clockIn(Long workerId, Long siteId) {
        Worker worker = workerRepository.findById(workerId)
            .orElseThrow(() -> new ApiException(ErrorCode.WORKER_NOT_FOUND,
                "Worker not found with ID: " + workerId, HttpStatus.NOT_FOUND));
        if (!worker.getActive()) {
            throw new ApiException(ErrorCode.WORKER_INACTIVE,
                "Worker is not active: " + worker.getName(), HttpStatus.BAD_REQUEST);
        }

        Site site = siteRepository.findById(siteId)
            .orElseThrow(() -> new ApiException(ErrorCode.SITE_NOT_FOUND,
                "Site not found with ID: " + siteId, HttpStatus.NOT_FOUND));
        if (!site.getActive()) {
            throw new ApiException(ErrorCode.SITE_INACTIVE,
                "Site is not active: " + site.getSiteName(), HttpStatus.BAD_REQUEST);
        }

        attendanceLogRepository.findActiveByWorkerId(workerId).ifPresent(existing -> {
            throw new ApiException(ErrorCode.DUPLICATE_CLOCK_IN,
                "Worker is already clocked in at Site: " + existing.getSite().getSiteName(),
                HttpStatus.CONFLICT);
        });

        AttendanceLog log = new AttendanceLog();
        log.setWorker(worker);
        log.setSite(site);
        LocalDateTime now = LocalDateTime.now();
        log.setClockInTime(now);
        AttendanceLog saved = attendanceLogRepository.save(log);

        Map<String, Object> workerData = Map.of(
            "workerId", workerId,
            "workerName", worker.getName(),
            "siteId", siteId,
            "siteName", site.getSiteName(),
            "clockInTime", now.toString(),
            "designation", worker.getDesignation().name()
        );
        try {
            redisTemplate.opsForValue().set(ACTIVE_WORKERS_KEY + ":" + workerId, workerData, Duration.ofHours(activeTtlHours));
        } catch (RuntimeException e) {
            log.warn("Failed to add worker {} to Redis active set: {}", workerId, e.getMessage());
        }

        return AttendanceResponse.fromEntity(saved);
    }

    @Transactional
    public AttendanceResponse clockOut(Long workerId) {
        AttendanceLog attendance = attendanceLogRepository.findActiveByWorkerId(workerId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_CLOCKED_IN,
                "Worker is not currently clocked in", HttpStatus.BAD_REQUEST));

        LocalDateTime clockOutTime = LocalDateTime.now();
        Duration duration = Duration.between(attendance.getClockInTime(), clockOutTime);
        double totalHours = duration.toMinutes() / 60.0;

        BigDecimal totalHoursBD = BigDecimal.valueOf(totalHours).setScale(2, RoundingMode.HALF_UP);
        attendance.setClockOutTime(clockOutTime);
        attendance.setTotalHoursWorked(totalHoursBD);

        if (totalHours > maxShiftHours) {
            attendance.setFlagged(true);
            log.warn("Attendance flagged for worker {} - shift exceeds {} hours", workerId, maxShiftHours);
        }

        if (totalHours > standardShiftHours) {
            applyOvertime(workerId, attendance, totalHours);
        } else {
            attendance.setOvertimeHours(BigDecimal.ZERO);
        }

        AttendanceLog saved = attendanceLogRepository.save(attendance);
        try {
            redisTemplate.delete(ACTIVE_WORKERS_KEY + ":" + workerId);
        } catch (RuntimeException e) {
            log.warn("Failed to remove worker {} from Redis active set: {}", workerId, e.getMessage());
        }

        return AttendanceResponse.fromEntity(saved);
    }

    public List<ActiveWorkerResponse> getActiveWorkers() {
        Set<String> keys;
        try {
            keys = redisTemplate.keys(ACTIVE_WORKERS_KEY + ":*");
        } catch (RuntimeException e) {
            log.warn("Failed to read active worker keys from Redis: {}", e.getMessage());
            return List.of();
        }
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }

        List<ActiveWorkerResponse> result = new ArrayList<>();
        for (String key : keys) {
            try {
                Object val = redisTemplate.opsForValue().get(key);
                if (val instanceof Map<?, ?> map) {
                    result.add(ActiveWorkerResponse.fromMap((Map<String, Object>) map));
                }
            } catch (RuntimeException e) {
                log.warn("Failed to read active worker from Redis key {}: {}", key, e.getMessage());
            }
        }
        return result;
    }

    public PagedResponse<AttendanceResponse> getAttendanceLog(Long workerId, LocalDate from, LocalDate to, int page, int size) {
        workerRepository.findById(workerId)
            .orElseThrow(() -> new ApiException(ErrorCode.WORKER_NOT_FOUND,
                "Worker not found with ID: " + workerId, HttpStatus.NOT_FOUND));

        Pageable pageable = PageRequest.of(page, size, Sort.by("clockInTime").descending());
        Page<AttendanceLog> results = attendanceLogRepository
            .findByWorkerAndDateRange(workerId, from.atStartOfDay(), to.atTime(23, 59, 59), pageable);

        return PagedResponse.of(
            results.getContent().stream().map(AttendanceResponse::fromEntity).collect(Collectors.toList()),
            results.getTotalElements(),
            results.getTotalPages(),
            page
        );
    }

    private void applyOvertime(Long workerId, AttendanceLog attendance, double totalHours) {
        double rawOvertimeHours = totalHours - standardShiftHours;
        int year = attendance.getClockInTime().getYear();
        int month = attendance.getClockInTime().getMonthValue();
        BigDecimal existingMonthlyOT = attendanceLogRepository.sumOvertimeHoursForMonth(workerId, year, month);
        double remainingCap = monthlyCapHours - existingMonthlyOT.doubleValue();
        double cappedOvertimeHours = Math.min(rawOvertimeHours, Math.max(0, remainingCap));
        BigDecimal cappedHours = BigDecimal.valueOf(cappedOvertimeHours).setScale(2, RoundingMode.HALF_UP);
        attendance.setOvertimeHours(cappedHours);

        if (cappedOvertimeHours <= 0) {
            return;
        }

        BigDecimal dailyWage = attendance.getWorker().getDailyWageRate();
        BigDecimal hourlyWage = dailyWage.divide(BigDecimal.valueOf(8), 4, RoundingMode.HALF_UP);
        BigDecimal overtimeAmount = calculateOvertimeAmount(hourlyWage, cappedOvertimeHours);
        BigDecimal effectiveRate = overtimeAmount.divide(BigDecimal.valueOf(cappedOvertimeHours), 4, RoundingMode.HALF_UP);

        OvertimeEntry overtimeEntry = new OvertimeEntry();
        overtimeEntry.setWorker(attendance.getWorker());
        overtimeEntry.setAttendanceLog(attendance);
        overtimeEntry.setDate(attendance.getClockInTime().toLocalDate());
        overtimeEntry.setOvertimeHours(cappedHours);
        overtimeEntry.setOvertimeRateApplied(effectiveRate.setScale(2, RoundingMode.HALF_UP));
        overtimeEntry.setAmount(overtimeAmount.setScale(2, RoundingMode.HALF_UP));
        attendance.setOvertimeEntry(overtimeEntry);
    }

    private BigDecimal calculateOvertimeAmount(BigDecimal hourlyWage, double cappedOvertimeHours) {
        if (cappedOvertimeHours <= tier1Hours) {
            return hourlyWage
                .multiply(BigDecimal.valueOf(tier1Multiplier))
                .multiply(BigDecimal.valueOf(cappedOvertimeHours));
        }

        BigDecimal tier1Amount = hourlyWage
            .multiply(BigDecimal.valueOf(tier1Multiplier))
            .multiply(BigDecimal.valueOf(tier1Hours));
        BigDecimal tier2Amount = hourlyWage
            .multiply(BigDecimal.valueOf(tier2Multiplier))
            .multiply(BigDecimal.valueOf(cappedOvertimeHours - tier1Hours));
        return tier1Amount.add(tier2Amount);
    }
}
