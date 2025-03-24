package com.example.worker_service.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

@Document(collection = "training_status")
public class TrainingStatus {
    @Id
    private String id; // trainingId
    private String status; // e.g., "IN_PROGRESS", "COMPLETED"
    private int progress; // Percentage (0-100)
    private Map<String, double[]> weights; // Layer name -> weight array

    // Constructors
    public TrainingStatus() {}

    public TrainingStatus(String id, String status, int progress) {
        this.id = id;
        this.status = status;
        this.progress = progress;
        this.weights = null; // Default to null if weights not provided
    }

    public TrainingStatus(String id, String status, int progress, Map<String, double[]> weights) {
        this.id = id;
        this.status = status;
        this.progress = progress;
        this.weights = weights;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public Map<String, double[]> getWeights() {
        return weights;
    }

    public void setWeights(Map<String, double[]> weights) {
        this.weights = weights;
    }
}