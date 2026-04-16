package com.hackathon.resourceallocation.repository;

import com.hackathon.resourceallocation.model.Need;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NeedRepository extends JpaRepository<Need, Long> {

    List<Need> findByStatusOrderByPriorityScoreDesc(Need.NeedStatus status);

    List<Need> findByCategoryOrderByPriorityScoreDesc(Need.Category category);

    List<Need> findByUrgencyOrderByCreatedAtDesc(Need.UrgencyLevel urgency);

    List<Need> findAllByOrderByPriorityScoreDesc();

    // For map view: all needs that have coordinates
    @Query("SELECT n FROM Need n WHERE n.latitude IS NOT NULL AND n.longitude IS NOT NULL ORDER BY n.priorityScore DESC")
    List<Need> findAllWithCoordinates();

    // Needs that haven't been analyzed by AI yet
    @Query("SELECT n FROM Need n WHERE n.urgency IS NULL OR n.priorityScore IS NULL")
    List<Need> findUnanalyzedNeeds();

    // Count by status
    long countByStatus(Need.NeedStatus status);

    // Count by urgency
    long countByUrgency(Need.UrgencyLevel urgency);

    // Filter by both status and category
    List<Need> findByStatusAndCategoryOrderByPriorityScoreDesc(Need.NeedStatus status, Need.Category category);

    // Search by keyword in title or description
    @Query("SELECT n FROM Need n WHERE LOWER(n.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(n.description) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "ORDER BY n.priorityScore DESC")
    List<Need> searchByKeyword(@Param("keyword") String keyword);
}