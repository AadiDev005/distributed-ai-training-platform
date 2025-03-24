package com.example.worker_service.service;

import com.example.worker_service.model.TrainingStatus;
import com.example.worker_service.util.CustomDataSetIterator;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
public class WorkerService {

    private static final Logger logger = LoggerFactory.getLogger(WorkerService.class);
    private static final int BATCH_SIZE = 32; // Consistent with CustomDataSetIterator

    @Autowired
    private MongoTemplate mongoTemplate;

    @KafkaListener(topics = "training-tasks", groupId = "workers")
    public void processTask(@Payload String datasetUrl, @Header(KafkaHeaders.RECEIVED_KEY) String trainingId) {
        try {
            logger.info("Received task for trainingId: {}, datasetUrl: {}", trainingId, datasetUrl);

            // Create iterator for the S3 dataset chunk
            DataSetIterator iterator = new CustomDataSetIterator(datasetUrl, BATCH_SIZE);

            // Build and train the model dynamically based on dataset properties
            MultiLayerNetwork model = buildModel(iterator.inputColumns(), iterator.totalOutcomes());
            model.fit(iterator);

            // Extract model weights after training
            Map<String, double[]> weights = extractWeights(model);

            logger.info("Training completed for dataset: {}", datasetUrl);

            // Update status in MongoDB with weights
            updateStatus(trainingId, weights);

        } catch (IOException e) {
            logger.error("Failed to load dataset for trainingId: {}, datasetUrl: {}", trainingId, datasetUrl, e);
            throw new RuntimeException("Dataset loading failed", e);
        } catch (Exception e) {
            logger.error("Failed to process task for trainingId: {}, datasetUrl: {}", trainingId, datasetUrl, e);
            throw new RuntimeException("Task processing failed", e);
        }
    }

    private MultiLayerNetwork buildModel(int inputColumns, int totalOutcomes) {
        MultiLayerConfiguration config = new NeuralNetConfiguration.Builder()
                .seed(123) // Reproducibility
                .updater(new Adam(0.001)) // Learning rate
                .list()
                .layer(0, new DenseLayer.Builder()
                        .nIn(inputColumns) // Dynamic input size from dataset
                        .nOut(100) // Hidden layer size (configurable)
                        .activation(Activation.RELU)
                        .build())
                .layer(1, new OutputLayer.Builder()
                        .nIn(100)
                        .nOut(totalOutcomes) // Dynamic output size from dataset
                        .activation(Activation.SOFTMAX)
                        .lossFunction(LossFunctions.LossFunction.MCXENT) // Multi-class cross-entropy
                        .build())
                .build();

        MultiLayerNetwork model = new MultiLayerNetwork(config);
        model.init();
        return model;
    }

    private Map<String, double[]> extractWeights(MultiLayerNetwork model) {
        Map<String, double[]> weights = new HashMap<>();
        // Get all parameters (weights and biases) from the model
        INDArray allParams = model.params(); // Returns a single INDArray with all parameters
        // Alternatively, use paramTable() for per-layer breakdown
        Map<String, INDArray> paramTable = model.paramTable();

        for (Map.Entry<String, INDArray> entry : paramTable.entrySet()) {
            String paramName = entry.getKey(); // e.g., "0_W" (weights), "0_b" (bias)
            INDArray paramArray = entry.getValue();
            weights.put(paramName, paramArray.data().asDouble());
        }
        return weights;
    }

    private void updateStatus(String trainingId, Map<String, double[]> weights) {
        Query query = new Query(Criteria.where("id").is(trainingId));
        Update update = new Update()
                .inc("progress", 25) // 25% per chunk (assuming 4 chunks total)
                .set("weights", weights); // Store weights in MongoDB

        mongoTemplate.updateFirst(query, update, TrainingStatus.class);

        TrainingStatus status = mongoTemplate.findOne(query, TrainingStatus.class);
        if (status != null && status.getProgress() >= 100) {
            update.set("status", "COMPLETED");
            mongoTemplate.updateFirst(query, update, TrainingStatus.class);
            logger.info("Training completed for trainingId: {}", trainingId);
        } else {
            logger.info("Updated progress for trainingId: {} to {}", trainingId, status != null ? status.getProgress() : 0);
        }
    }
}