package com.hackathon.resourceallocation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageReportResponse {

    private Long needId;
    private String message;
    private int imageCount;
    private List<String> imageUrls;

    // PENDING_AI_ANALYSIS | REANALYSIS_TRIGGERED | COMPLETE
    private String status;
}