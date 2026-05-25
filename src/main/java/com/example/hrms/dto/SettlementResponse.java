package com.example.hrms.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SettlementResponse {

    private Long workerId;
    private String workerName;
    private String month;
    private int entriesSettled;
    private BigDecimal totalAmountSettled;
}
