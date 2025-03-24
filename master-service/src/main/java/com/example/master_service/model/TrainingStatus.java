package com.example.master_service.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "training_status")
public class TrainingStatus {
    @Id
    private String id;          // trainingId
    private String status;      // e.g., "RUNNING", "COMPLETED"
    private int progress;       // Percentage (0-100)

    // Default constructor for MongoDB
    public TrainingStatus() {}

    public TrainingStatus(String id, String status, int progress) {
        this.id = id;
        this.status = status;
        this.progress = progress;
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
}