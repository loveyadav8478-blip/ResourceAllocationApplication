package com.hackathon.resourceallocation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.hackathon.resourceallocation.model.Task;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "needs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Need {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private UrgencyLevel urgency;

    @Column(name = "priority_score")
    private Integer priorityScore;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    @Builder.Default
    private NeedStatus status = NeedStatus.OPEN;

    private Double latitude;
    private Double longitude;

    @Column(name = "location_name")
    private String locationName;

    @Column(name = "reporter_name")
    private String reporterName;

    @Column(name = "reporter_contact")
    private String reporterContact;

    @Column(name = "ai_reasoning", columnDefinition = "TEXT")
    private String aiReasoning;

    @Column(name = "suggested_skills")
    private String suggestedSkills; // comma-separated

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "need", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Task> tasks;

    private String source;
    //Enums
    public enum Category {
        FOOD, MEDICAL, SHELTER, WATER, OTHER
    }

    public enum UrgencyLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum NeedStatus {
        OPEN, ASSIGNED, IN_PROGRESS, RESOLVED
    }
}
