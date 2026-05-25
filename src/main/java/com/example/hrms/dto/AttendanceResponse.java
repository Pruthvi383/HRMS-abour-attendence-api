package com.example.hrms.dto;

import com.example.hrms.entity.AttendanceLog;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AttendanceResponse {

    private Long id;
    private Long workerId;
    private String workerName;
    private Long siteId;
    private String siteName;
    private LocalDateTime clockInTime;
    private LocalDateTime clockOutTime;
    private BigDecimal totalHoursWorked;
    private BigDecimal overtimeHours;
    private Boolean flagged;

    public static AttendanceResponse fromEntity(AttendanceLog log) {
        return AttendanceResponse.builder()
            .id(log.getId())
            .workerId(log.getWorker().getId())
            .workerName(log.getWorker().getName())
            .siteId(log.getSite().getId())
            .siteName(log.getSite().getSiteName())
            .clockInTime(log.getClockInTime())
            .clockOutTime(log.getClockOutTime())
            .totalHoursWorked(log.getTotalHoursWorked())
            .overtimeHours(log.getOvertimeHours())
            .flagged(log.getFlagged())
            .build();
    }
}
