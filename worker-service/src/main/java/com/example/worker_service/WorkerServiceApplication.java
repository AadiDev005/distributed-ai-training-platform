package com.example.worker_service;

import com.example.worker_service.util.CustomDataSetIterator;
import org.nd4j.linalg.dataset.DataSet;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WorkerServiceApplication {
	public static void main(String[] args) {
		SpringApplication.run(WorkerServiceApplication.class, args);

		try {
			CustomDataSetIterator iterator = new CustomDataSetIterator(
					"s3://aadi-dataset-bucket-2025/test.csv",
					32
			);
			System.out.println("Iterator initialized successfully!");

			while (iterator.hasNext()) {
				DataSet dataSet = iterator.next();
				System.out.println("Features: " + dataSet.getFeatures());
				System.out.println("Labels: " + dataSet.getLabels());
			}
		} catch (Exception e) {
			System.err.println("Error initializing or using CustomDataSetIterator: " + e.getMessage());
			e.printStackTrace();
		}
	}
}