package com.example.hrms.event;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OvertimeSettledEvent {

    private final Long workerId;
    private final String workerName;
    private final String phone;
    private final String month;
    private final BigDecimal totalAmount;
}
