package com.hackathon.resourceallocation.service;

import com.hackathon.resourceallocation.dto.NeedRequest;
import com.hackathon.resourceallocation.dto.NeedResponse;
import com.hackathon.resourceallocation.exception.ResourceNotFoundException;
import com.hackathon.resourceallocation.model.Need;
import com.hackathon.resourceallocation.repository.NeedRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class NeedsService {

    private final NeedRepository needRepository;
    private final AIService aiService;

    @Transactional
    public NeedResponse createNeed(NeedRequest request) {
        Need need = Need.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .locationName(request.getLocationName())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .reporterName(request.getReporterName())
                .reporterContact(request.getReporterContact())
                .status(Need.NeedStatus.OPEN)
                .build();

        Need saved = needRepository.save(need);
        log.info("Created new need with ID: {}", saved.getId());

        // Trigger async AI analysis
        analyzeNeedAsync(saved.getId());

        return NeedResponse.from(saved);
    }

    @Async
    @Transactional
    public void analyzeNeedAsync(Long needId) {
        needRepository.findById(needId).ifPresent(need -> {
            log.info("Starting AI analysis for need ID: {}", needId);
            try {
                AIService.AIAnalysisResult result =
                        aiService.analyzeNeed(need.getTitle(), need.getDescription());

                need.setCategory(Need.Category.valueOf(result.category()));
                need.setUrgency(Need.UrgencyLevel.valueOf(result.urgency()));
                need.setPriorityScore(result.priorityScore());
                need.setAiReasoning(result.reasoning());
                need.setSuggestedSkills(result.suggestedSkills());

                needRepository.save(need);
                log.info("AI analysis complete for need ID {}: {} / {} / score {}",
                        needId, result.category(), result.urgency(), result.priorityScore());
            } catch (Exception e) {
                log.error("Async AI analysis failed for need ID {}: {}", needId, e.getMessage());
            }
        });
    }

    @Transactional
    public NeedResponse triggerAnalysis(Long id) {
        Need need = getOrThrow(id);
        AIService.AIAnalysisResult result = aiService.analyzeNeed(need.getTitle(), need.getDescription());

        need.setCategory(Need.Category.valueOf(result.category()));
        need.setUrgency(Need.UrgencyLevel.valueOf(result.urgency()));
        need.setPriorityScore(result.priorityScore());
        need.setAiReasoning(result.reasoning());
        need.setSuggestedSkills(result.suggestedSkills());

        return NeedResponse.from(needRepository.save(need));
    }

    public List<NeedResponse> getAllNeeds(String status, String category, String keyword) {
        if (keyword != null && !keyword.isBlank()) {
            return needRepository.searchByKeyword(keyword)
                    .stream().map(NeedResponse::from).collect(Collectors.toList());
        }
        if (status != null && category != null) {
            return needRepository.findByStatusAndCategoryOrderByPriorityScoreDesc(
                            Need.NeedStatus.valueOf(status.toUpperCase()),
                            Need.Category.valueOf(category.toUpperCase()))
                    .stream().map(NeedResponse::from).collect(Collectors.toList());
        }
        if (status != null) {
            return needRepository.findByStatusOrderByPriorityScoreDesc(
                            Need.NeedStatus.valueOf(status.toUpperCase()))
                    .stream().map(NeedResponse::from).collect(Collectors.toList());
        }
        if (category != null) {
            return needRepository.findByCategoryOrderByPriorityScoreDesc(
                            Need.Category.valueOf(category.toUpperCase()))
                    .stream().map(NeedResponse::from).collect(Collectors.toList());
        }
        return needRepository.findAllByOrderByPriorityScoreDesc()
                .stream().map(NeedResponse::from).collect(Collectors.toList());
    }

    public List<NeedResponse> getNeedsForMap() {
        return needRepository.findAllWithCoordinates()
                .stream().map(NeedResponse::from).collect(Collectors.toList());
    }

    public NeedResponse getNeedById(Long id) {
        return NeedResponse.from(getOrThrow(id));
    }

    @Transactional
    public NeedResponse updateStatus(Long id, String status) {
        Need need = getOrThrow(id);
        need.setStatus(Need.NeedStatus.valueOf(status.toUpperCase()));
        return NeedResponse.from(needRepository.save(need));
    }

    @Transactional
    public void deleteNeed(Long id) {
        needRepository.deleteById(id);
    }

    private Need getOrThrow(Long id) {
        return needRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Need not found with ID: " + id));
    }
}