package com.hackathon.resourceallocation.dto;

import com.hackathon.resourceallocation.model.Need;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NeedResponse {
    private Long id;
    private String title;
    private String description;
    private String category;
    private String urgency;
    private Integer priorityScore;
    private String status;
    private Double latitude;
    private Double longitude;
    private String locationName;
    private String reporterName;
    private String reporterContact;
    private String aiReasoning;
    private String suggestedSkills;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static NeedResponse from(Need need) {
        return NeedResponse.builder()
                .id(need.getId())
                .title(need.getTitle())
                .description(need.getDescription())
                .category(need.getCategory() != null ? need.getCategory().name() : null)
                .urgency(need.getUrgency() != null ? need.getUrgency().name() : null)
                .priorityScore(need.getPriorityScore())
                .status(need.getStatus() != null ? need.getStatus().name() : null)
                .latitude(need.getLatitude())
                .longitude(need.getLongitude())
                .locationName(need.getLocationName())
                .reporterName(need.getReporterName())
                .reporterContact(need.getReporterContact())
                .aiReasoning(need.getAiReasoning())
                .suggestedSkills(need.getSuggestedSkills())
                .createdAt(need.getCreatedAt())
                .updatedAt(need.getUpdatedAt())
                .build();
    }
}