package com.example.hrms.controller;

import com.example.hrms.dto.OvertimeSummaryResponse;
import com.example.hrms.dto.SettlementResponse;
import com.example.hrms.service.OvertimeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/overtime")
@Slf4j
public class OvertimeController {

    private final OvertimeService overtimeService;

    public OvertimeController(OvertimeService overtimeService) {
        this.overtimeService = overtimeService;
    }

    @GetMapping("/summary/{workerId}")
    public ResponseEntity<OvertimeSummaryResponse> getSummary(
            @PathVariable Long workerId,
            @RequestParam String month) {
        return ResponseEntity.ok(overtimeService.getMonthlySummary(workerId, month));
    }

    @PostMapping("/settle/{workerId}")
    public ResponseEntity<SettlementResponse> settle(
            @PathVariable Long workerId,
            @RequestParam String month) {
        return ResponseEntity.ok(overtimeService.settleMonth(workerId, month));
    }
}
