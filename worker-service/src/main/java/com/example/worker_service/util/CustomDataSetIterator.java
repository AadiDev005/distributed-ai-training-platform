package com.example.worker_service.util;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CustomDataSetIterator implements DataSetIterator {
    private final String s3Url;
    private final S3Client s3Client;
    private final List<float[]> features;
    private final List<float[]> labels;
    private int cursor = 0;
    private final int batchSize;
    private final int inputColumns;
    private final int totalOutcomes;

    public CustomDataSetIterator(String s3Url, int batchSize) throws IOException {
        this.s3Url = s3Url; // e.g., "s3://aadi-dataset-bucket-2025/test.csv"
        this.batchSize = batchSize;
        this.s3Client = S3Client.builder()
                .region(Region.AP_SOUTH_1)
                .build();
        this.features = new ArrayList<>();
        this.labels = new ArrayList<>();

        loadDataFromS3();

        this.inputColumns = 2; // daily_usage_gb, peak_usage_gb
        this.totalOutcomes = 3; // 0 = Low, 1 = Medium, 2 = High
    }

    private void loadDataFromS3() throws IOException {
        String[] parts = s3Url.replace("s3://", "").split("/", 2);
        String bucket = parts[0]; // "aadi-dataset-bucket-2025"
        String key = parts[1];    // "test.csv"

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getObjectRequest);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(s3Object))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false; // Skip header
                    continue;
                }
                String[] values = line.split(",");
                float[] featureArray = new float[2];
                featureArray[0] = Float.parseFloat(values[0]); // daily_usage_gb
                featureArray[1] = Float.parseFloat(values[1]); // peak_usage_gb

                float[] labelArray = new float[1];
                labelArray[0] = Float.parseFloat(values[2]); // label

                features.add(featureArray);
                labels.add(labelArray);
            }
        }
    }

    @Override
    public DataSet next(int num) {
        int from = cursor;
        int to = Math.min(from + num, features.size());
        cursor = to;

        int numExamples = to - from;
        if (numExamples <= 0) {
            return null;
        }

        INDArray featureArray = Nd4j.create(numExamples, inputColumns);
        INDArray labelArray = Nd4j.create(numExamples, totalOutcomes);

        for (int i = 0; i < numExamples; i++) {
            float[] feature = features.get(from + i);
            float label = labels.get(from + i)[0];

            featureArray.putRow(i, Nd4j.create(feature));
            labelArray.putScalar(i, (int) label, 1.0); // One-hot encoding
        }

        return new DataSet(featureArray, labelArray);
    }

    @Override
    public int inputColumns() {
        return inputColumns;
    }

    @Override
    public int totalOutcomes() {
        return totalOutcomes;
    }

    @Override
    public boolean resetSupported() {
        return true;
    }

    @Override
    public boolean asyncSupported() {
        return false;
    }

    @Override
    public void reset() {
        cursor = 0;
    }

    @Override
    public int batch() {
        return batchSize;
    }

    @Override
    public void setPreProcessor(DataSetPreProcessor preProcessor) {
    }

    @Override
    public DataSetPreProcessor getPreProcessor() {
        return null;
    }

    @Override
    public List<String> getLabels() {
        return List.of("Low", "Medium", "High");
    }

    @Override
    public boolean hasNext() {
        return cursor < features.size();
    }

    @Override
    public DataSet next() {
        return next(batchSize);
    }
}