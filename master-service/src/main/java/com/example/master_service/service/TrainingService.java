package com.example.master_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.example.master_service.model.TrainingStatus;

@Service
public class TrainingService {

    private static final Logger logger = LoggerFactory.getLogger(TrainingService.class);
    private static final int NUM_CHUNKS = 4; // Configurable number of chunks

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private S3Client s3Client; // Add S3Client dependency

    public String startTraining(String datasetUrl) {
        String trainingId = UUID.randomUUID().toString();
        logger.info("Starting training for dataset: {}, trainingId: {}", datasetUrl, trainingId);

        // Split dataset into chunks and upload to S3
        List<String> chunkUrls = splitDataset(datasetUrl);

        // Publish each chunk to Kafka
        for (String chunkUrl : chunkUrls) {
            kafkaTemplate.send("training-tasks", trainingId, chunkUrl);
            logger.info("Published chunk to Kafka: {}", chunkUrl);
        }

        // Save initial status to MongoDB
        TrainingStatus initialStatus = new TrainingStatus(trainingId, "RUNNING", 0);
        mongoTemplate.save(initialStatus);
        logger.info("Saved initial status for trainingId: {}", trainingId);

        return trainingId;
    }

    public TrainingStatus getTrainingStatus(String trainingId) {
        return mongoTemplate.findById(trainingId, TrainingStatus.class);
    }

    private List<String> splitDataset(String datasetUrl) {
        List<String> chunkUrls = new ArrayList<>();
        String bucket = datasetUrl.replace("s3://", "").split("/")[0];
        String key = datasetUrl.replace("s3://", "").split("/", 2)[1];
        File datasetFile = downloadDataset(bucket, key);

        try {
            splitAndUploadDataset(datasetFile, bucket, chunkUrls);
            datasetFile.delete(); // Clean up original file
        } catch (Exception e) {
            logger.error("Failed to split and upload dataset: {}", datasetUrl, e);
            throw new RuntimeException("Dataset splitting failed", e);
        }

        return chunkUrls;
    }

    private File downloadDataset(String bucket, String key) {
        File localFile = new File("/tmp/" + key);
        try {
            s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build(), localFile.toPath());
            logger.info("Downloaded dataset from s3://{}/{}", bucket, key);
            return localFile;
        } catch (Exception e) {
            logger.error("Failed to download dataset from S3: s3://{}/{}", bucket, key, e);
            throw new RuntimeException("Download failed", e);
        }
    }

    private void splitAndUploadDataset(File datasetFile, String bucket, List<String> chunkUrls) throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader(datasetFile))) {
            int totalLines = (int) Files.lines(datasetFile.toPath()).count();
            int chunkSize = (int) Math.ceil((double) totalLines / NUM_CHUNKS);
            logger.info("Splitting {} lines into {} chunks, chunk size: {}", totalLines, NUM_CHUNKS, chunkSize);

            String line;
            int lineCount = 0;
            int chunkIndex = 0;
            FileWriter writer = new FileWriter("/tmp/test.csv-chunk-" + chunkIndex);

            while ((line = reader.readLine()) != null) {
                if (lineCount > 0 && lineCount % chunkSize == 0) {
                    writer.close();
                    String chunkUrl = uploadChunk(bucket, chunkIndex);
                    chunkUrls.add(chunkUrl);
                    chunkIndex++;
                    writer = new FileWriter("/tmp/test.csv-chunk-" + chunkIndex);
                }
                writer.write(line + "\n");
                lineCount++;
            }
            writer.close();
            String chunkUrl = uploadChunk(bucket, chunkIndex);
            chunkUrls.add(chunkUrl);
        }
    }

    private String uploadChunk(String bucket, int chunkIndex) {
        String chunkKey = "test.csv-chunk-" + chunkIndex;
        File chunkFile = new File("/tmp/" + chunkKey);
        try {
            s3Client.putObject(PutObjectRequest.builder().bucket(bucket).key(chunkKey).build(), chunkFile.toPath());
            String chunkUrl = "s3://" + bucket + "/" + chunkKey;
            logger.info("Uploaded chunk to S3: {}", chunkUrl);
            return chunkUrl;
        } finally {
            chunkFile.delete(); // Clean up even on failure
        }
    }
}