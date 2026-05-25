package com.example.hrms.service;

import com.example.hrms.dto.OvertimeDailyBreakdown;
import com.example.hrms.dto.OvertimeSummaryResponse;
import com.example.hrms.dto.SettlementResponse;
import com.example.hrms.entity.OvertimeEntry;
import com.example.hrms.entity.Worker;
import com.example.hrms.enums.SettlementStatus;
import com.example.hrms.event.OvertimeSettledEvent;
import com.example.hrms.exception.ApiException;
import com.example.hrms.exception.ErrorCode;
import com.example.hrms.repository.OvertimeEntryRepository;
import com.example.hrms.repository.WorkerRepository;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class OvertimeService {

    private final WorkerRepository workerRepository;
    private final OvertimeEntryRepository overtimeEntryRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OvertimeService(WorkerRepository workerRepository,
                           OvertimeEntryRepository overtimeEntryRepository,
                           ApplicationEventPublisher eventPublisher) {
        this.workerRepository = workerRepository;
        this.overtimeEntryRepository = overtimeEntryRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public OvertimeSummaryResponse getMonthlySummary(Long workerId, String month) {
        Worker worker = workerRepository.findById(workerId)
            .orElseThrow(() -> new ApiException(ErrorCode.WORKER_NOT_FOUND,
                "Worker not found with ID: " + workerId, HttpStatus.NOT_FOUND));

        YearMonth ym = YearMonth.parse(month);
        List<OvertimeEntry> entries = overtimeEntryRepository
            .findByWorkerAndMonth(workerId, ym.getYear(), ym.getMonthValue());

        BigDecimal totalHours = entries.stream()
            .map(OvertimeEntry::getOvertimeHours)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAmount = entries.stream()
            .map(OvertimeEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean allSettled = !entries.isEmpty() && entries.stream()
            .allMatch(e -> e.getSettlementStatus() == SettlementStatus.SETTLED);
        List<OvertimeDailyBreakdown> breakdown = entries.stream()
            .map(OvertimeDailyBreakdown::fromEntity)
            .collect(Collectors.toList());

        return OvertimeSummaryResponse.builder()
            .workerId(workerId)
            .workerName(worker.getName())
            .month(month)
            .totalOvertimeHours(totalHours)
            .totalPayoutAmount(totalAmount)
            .settlementStatus(allSettled ? SettlementStatus.SETTLED : SettlementStatus.PENDING)
            .dailyBreakdown(breakdown)
            .build();
    }

    @Transactional
    public SettlementResponse settleMonth(Long workerId, String month) {
        Worker worker = workerRepository.findById(workerId)
            .orElseThrow(() -> new ApiException(ErrorCode.WORKER_NOT_FOUND,
                "Worker not found with ID: " + workerId, HttpStatus.NOT_FOUND));

        YearMonth ym = YearMonth.parse(month);
        YearMonth currentMonth = YearMonth.now();
        if (!ym.isBefore(currentMonth)) {
            throw new ApiException(ErrorCode.CURRENT_MONTH_SETTLEMENT,
                "Cannot settle current or future months. Only completed months can be settled.",
                HttpStatus.BAD_REQUEST);
        }

        List<OvertimeEntry> entries = overtimeEntryRepository
            .findByWorkerAndMonth(workerId, ym.getYear(), ym.getMonthValue());
        if (entries.isEmpty()) {
            throw new ApiException(ErrorCode.NO_PENDING_OVERTIME,
                "No overtime entries found for worker in " + month, HttpStatus.NOT_FOUND);
        }

        boolean anySettled = entries.stream()
            .anyMatch(e -> e.getSettlementStatus() == SettlementStatus.SETTLED);
        if (anySettled) {
            throw new ApiException(ErrorCode.ALREADY_SETTLED,
                "Overtime for this worker and month is already settled or partially settled",
                HttpStatus.CONFLICT);
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (OvertimeEntry entry : entries) {
            entry.setSettlementStatus(SettlementStatus.SETTLED);
            totalAmount = totalAmount.add(entry.getAmount());
        }
        overtimeEntryRepository.saveAll(entries);
        eventPublisher.publishEvent(new OvertimeSettledEvent(
            workerId, worker.getName(), worker.getPhone(), month, totalAmount));

        return SettlementResponse.builder()
            .workerId(workerId)
            .workerName(worker.getName())
            .month(month)
            .entriesSettled(entries.size())
            .totalAmountSettled(totalAmount)
            .build();
    }
}
