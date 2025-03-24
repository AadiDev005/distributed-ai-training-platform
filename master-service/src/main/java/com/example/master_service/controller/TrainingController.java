package com.example.master_service.controller;

import com.example.master_service.model.TrainingRequest;
import com.example.master_service.model.TrainingStatus;
import com.example.master_service.service.TrainingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class TrainingController {

    @Autowired
    private TrainingService trainingService;

    @PostMapping("/train")
    public ResponseEntity<String> startTraining(@RequestBody TrainingRequest request) {
        // Start training with the dataset URL (e.g., s3://aadi-dataset-bucket-2025/test.csv)
        String trainingId = trainingService.startTraining(request.getDatasetUrl());
        return ResponseEntity.ok(trainingId);
    }

    @GetMapping("/status/{id}")
    public ResponseEntity<TrainingStatus> getStatus(@PathVariable("id") String trainingId) {
        TrainingStatus status = trainingService.getTrainingStatus(trainingId);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }
}