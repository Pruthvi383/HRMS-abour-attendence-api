package com.example.hrms.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClockOutRequest {

    @NotNull
    private Long workerId;
}
