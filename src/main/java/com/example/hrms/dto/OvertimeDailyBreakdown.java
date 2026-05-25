package com.example.hrms.dto;

import com.example.hrms.entity.OvertimeEntry;
import com.example.hrms.enums.SettlementStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OvertimeDailyBreakdown {

    private LocalDate date;
    private BigDecimal overtimeHours;
    private BigDecimal amount;
    private SettlementStatus status;

    public static OvertimeDailyBreakdown fromEntity(OvertimeEntry entry) {
        return OvertimeDailyBreakdown.builder()
            .date(entry.getDate())
            .overtimeHours(entry.getOvertimeHours())
            .amount(entry.getAmount())
            .status(entry.getSettlementStatus())
            .build();
    }
}
