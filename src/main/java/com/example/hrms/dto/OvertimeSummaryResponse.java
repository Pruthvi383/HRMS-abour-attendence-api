package com.example.hrms.dto;

import com.example.hrms.enums.SettlementStatus;
import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OvertimeSummaryResponse {

    private Long workerId;
    private String workerName;
    private String month;
    private BigDecimal totalOvertimeHours;
    private BigDecimal totalPayoutAmount;
    private SettlementStatus settlementStatus;
    private List<OvertimeDailyBreakdown> dailyBreakdown;
}
