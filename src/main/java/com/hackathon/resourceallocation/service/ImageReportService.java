package com.hackathon.resourceallocation.service;

import com.hackathon.resourceallocation.dto.ImageReportResponse;
import com.hackathon.resourceallocation.model.Need;
import com.hackathon.resourceallocation.model.NeedImage;
import com.hackathon.resourceallocation.repository.NeedImageRepository;
import com.hackathon.resourceallocation.repository.NeedRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ImageReportService {

    private final ImageStorageService storageService;
    private final GeminiVisionService visionService;
    private final NeedRepository needRepository;
    private final NeedImageRepository needImageRepository;

    // ── Step 1: Upload images, create Need, trigger async AI ─────

    /**
     * Called by the reporter when they upload images.
     * - Saves images to disk immediately (fast)
     * - Creates a draft Need with status OPEN
     * - Triggers async AI analysis (non-blocking)
     * - Returns immediately so the reporter isn't waiting
     */
    @Transactional
    public ImageReportResponse submitImageReport(
            List<MultipartFile> images,
            String reporterName,
            String reporterContact,
            String locationName,
            Double latitude,
            Double longitude) throws IOException {

        if (images == null || images.isEmpty()) {
            throw new IllegalArgumentException("At least one image is required.");
        }
        if (images.size() > 5) {
            throw new IllegalArgumentException("Maximum 5 images per report.");
        }

        // Create a placeholder Need immediately so reporter gets a reference ID
        Need need = Need.builder()
                .title("Image report — AI analysis pending...")
                .description("This need was reported via image. AI is analyzing the content.")
                .status(Need.NeedStatus.OPEN)
                .source("IMAGE")
                .reporterName(reporterName)
                .reporterContact(reporterContact)
                .locationName(locationName)
                .latitude(latitude)
                .longitude(longitude)
                .build();

        Need savedNeed = needRepository.save(need);
        log.info("Created image-based need placeholder with ID: {}", savedNeed.getId());

        // Store each image file and create NeedImage records
        List<NeedImage> savedImages = new ArrayList<>();
        for (MultipartFile file : images) {
            try {
                ImageStorageService.StoredFile stored = storageService.store(file);

                NeedImage needImage = NeedImage.builder()
                        .need(savedNeed)
                        .originalFilename(stored.originalFilename())
                        .storedFilename(stored.storedFilename())
                        .filePath(stored.relativePath())
                        .publicUrl(stored.publicUrl())
                        .contentType(stored.contentType())
                        .fileSizeBytes(stored.sizeBytes())
                        .aiProcessed(false)
                        .build();

                savedImages.add(needImageRepository.save(needImage));
                log.info("Saved image {} for need {}", stored.relativePath(), savedNeed.getId());

            } catch (Exception e) {
                log.error("Failed to store image for need {}: {}", savedNeed.getId(), e.getMessage());
                // Continue processing remaining images
            }
        }

        if (savedImages.isEmpty()) {
            throw new IOException("All image uploads failed. Please try again.");
        }

        // Kick off async AI analysis — reporter doesn't wait for this
        analyzeImagesAsync(savedNeed.getId(), savedImages);

        return ImageReportResponse.builder()
                .needId(savedNeed.getId())
                .message("Report submitted successfully. AI is analyzing your images.")
                .imageCount(savedImages.size())
                .imageUrls(savedImages.stream().map(NeedImage::getPublicUrl).collect(Collectors.toList()))
                .status("PENDING_AI_ANALYSIS")
                .build();
    }

    // ── Step 2 (Async): AI analyzes all images, updates the Need ──

    @Async
    @Transactional
    public void analyzeImagesAsync(Long needId, List<NeedImage> images) {
        log.info("Starting async AI vision analysis for need ID: {}", needId);

        needRepository.findById(needId).ifPresent(need -> {
            GeminiVisionService.ImageAnalysisResult bestResult = null;
            int bestScore = 0;

            for (NeedImage image : images) {
                try {
                    byte[] imageBytes = storageService.readBytes(image.getFilePath());
                    GeminiVisionService.ImageAnalysisResult result =
                            visionService.analyzeImage(imageBytes, image.getContentType());

                    // Store what AI extracted for this specific image
                    image.setAiExtractedText(
                            "[" + result.category() + " / " + result.urgency() + " / score:" +
                                    result.priorityScore() + "] " + result.description()
                    );
                    image.setAiProcessed(true);
                    needImageRepository.save(image);

                    // Keep the highest-scoring analysis across all images
                    if (result.priorityScore() > bestScore) {
                        bestScore = result.priorityScore();
                        bestResult = result;
                    }

                    log.info("Image {} analyzed: {} / {} / score {}",
                            image.getStoredFilename(),
                            result.category(), result.urgency(), result.priorityScore());

                } catch (Exception e) {
                    log.error("Vision analysis failed for image {}: {}",
                            image.getStoredFilename(), e.getMessage());
                }
            }

            // Update the Need with the best AI result found across all images
            if (bestResult != null) {
                need.setTitle(bestResult.title());
                need.setDescription(bestResult.description());
                need.setCategory(Need.Category.valueOf(bestResult.category()));
                need.setUrgency(Need.UrgencyLevel.valueOf(bestResult.urgency()));
                need.setPriorityScore(bestResult.priorityScore());
                need.setAiReasoning(bestResult.reasoning() +
                        (bestResult.locationHint() != null && !bestResult.locationHint().isBlank()
                                ? " Location hint from image: " + bestResult.locationHint()
                                : ""));
                need.setSuggestedSkills(bestResult.suggestedSkills());
                needRepository.save(need);
                log.info("Need {} updated from image analysis: {} / {} / score {}",
                        needId, bestResult.category(), bestResult.urgency(), bestResult.priorityScore());
            } else {
                // If all AI calls failed, mark for manual review
                need.setTitle("Image report — manual review required");
                need.setDescription("Images were uploaded but AI analysis failed. A coordinator should review this.");
                need.setUrgency(Need.UrgencyLevel.MEDIUM);
                need.setPriorityScore(50);
                need.setAiReasoning("AI analysis was unavailable. Manual review required.");
                needRepository.save(need);
                log.warn("All AI analyses failed for need {}, marked for manual review", needId);
            }
        });
    }

    // ── Re-analyze a specific need's images manually ──────────────

    @Transactional
    public ImageReportResponse reanalyzeImages(Long needId) {
        Need need = needRepository.findById(needId)
                .orElseThrow(() -> new IllegalArgumentException("Need not found: " + needId));

        List<NeedImage> images = needImageRepository.findByNeedId(needId);
        if (images.isEmpty()) {
            throw new IllegalArgumentException("No images found for need: " + needId);
        }

        // Reset AI processed flag
        images.forEach(img -> {
            img.setAiProcessed(false);
            needImageRepository.save(img);
        });

        analyzeImagesAsync(needId, images);

        return ImageReportResponse.builder()
                .needId(needId)
                .message("Re-analysis triggered for " + images.size() + " image(s).")
                .imageCount(images.size())
                .imageUrls(images.stream().map(NeedImage::getPublicUrl).collect(Collectors.toList()))
                .status("REANALYSIS_TRIGGERED")
                .build();
    }

    // ── Get all images for a need ─────────────────────────────────

    public List<NeedImage> getImagesForNeed(Long needId) {
        return needImageRepository.findByNeedId(needId);
    }
}