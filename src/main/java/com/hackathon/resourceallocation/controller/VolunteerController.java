package com.hackathon.resourceallocation.controller;

import com.hackathon.resourceallocation.dto.*;
import com.hackathon.resourceallocation.service.VolunteerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/volunteers")
@RequiredArgsConstructor
public class VolunteerController {

    private final VolunteerService volunteerService;

    @PostMapping
    public ResponseEntity<VolunteerResponse> create(@Valid @RequestBody VolunteerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(volunteerService.createVolunteer(request));
    }

    @GetMapping
    public ResponseEntity<List<VolunteerResponse>> getAll(
            @RequestParam(required = false) Boolean availableOnly) {
        return ResponseEntity.ok(volunteerService.getAllVolunteers(availableOnly));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VolunteerResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(volunteerService.getVolunteerById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<VolunteerResponse> update(
            @PathVariable Long id,
            @RequestBody VolunteerRequest request) {
        return ResponseEntity.ok(volunteerService.updateVolunteer(id, request));
    }

    @PatchMapping("/{id}/availability")
    public ResponseEntity<VolunteerResponse> updateAvailability(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        return ResponseEntity.ok(volunteerService.updateAvailability(id, body.get("available")));
    }
}