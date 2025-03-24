package com.example.master_service.model;

public class TrainingRequest {
    private String datasetUrl;

    // Default constructor for Jackson (JSON deserialization)
    public TrainingRequest() {}

    public TrainingRequest(String datasetUrl) {
        this.datasetUrl = datasetUrl;
    }

    public String getDatasetUrl() {
        return datasetUrl;
    }

    public void setDatasetUrl(String datasetUrl) {
        this.datasetUrl = datasetUrl;
    }
}