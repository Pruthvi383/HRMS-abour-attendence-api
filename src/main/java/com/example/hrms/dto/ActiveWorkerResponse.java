package com.example.hrms.dto;

import java.util.Map;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ActiveWorkerResponse {

    private Long workerId;
    private String workerName;
    private Long siteId;
    private String siteName;
    private String clockInTime;
    private String designation;

    public static ActiveWorkerResponse fromMap(Map<String, Object> map) {
        return ActiveWorkerResponse.builder()
            .workerId(Long.valueOf(map.get("workerId").toString()))
            .workerName((String) map.get("workerName"))
            .siteId(Long.valueOf(map.get("siteId").toString()))
            .siteName((String) map.get("siteName"))
            .clockInTime((String) map.get("clockInTime"))
            .designation((String) map.get("designation"))
            .build();
    }
}
