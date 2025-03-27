
# Distributed AI Training Platform

## Overview
The Distributed AI Training Platform is a scalable, cloud-native system for training AI models across multiple worker nodes, orchestrated by a master node. It leverages Kubernetes on Amazon EKS, Amazon DocumentDB for data storage, Amazon S3 for model storage, Apache Kafka for message passing, and Spring Boot for the application framework.

### Features
- **Scalability**: Distributes training tasks across multiple worker nodes.
- **Reliability**: Ensures high availability using Kubernetes deployments.
- **Security**: Connects securely to AWS services using SSL/TLS and IAM roles.
- **Monitoring**: Provides health checks via Spring Boot Actuator.

---

## Architecture

### Components
- **Master Service**: Orchestrates training, communicates via Kafka, stores metadata in DocumentDB, and uploads models to S3.
- **Worker Service**: Performs AI training, receives tasks via Kafka, and interacts with DocumentDB and S3.
- **Amazon DocumentDB**: Stores training metadata and intermediate results.
- **Amazon S3**: Stores datasets and trained models.
- **Apache Kafka**: Facilitates message passing between master and workers.
- **Kubernetes (EKS)**: Orchestrates deployments in the `ap-south-1` region.

---

## Prerequisites
- AWS Account with access to EKS, DocumentDB, S3, and IAM.
- `kubectl`, Docker, Maven, AWS CLI, and Java 17 installed.
- An EKS cluster in the `ap-south-1` region.
- IAM Role for Service Account (IRSA) configured for EKS.

---

## Setup Instructions

### 1. Clone the Repository
```bash
git clone https://github.com/adityatiwari/distributed-ai-training-platform.git
cd distributed-ai-training-platform
```

### 2. Configure AWS Resources
1. **DocumentDB**:
   - Create a cluster in `ap-south-1`.
   - Endpoint: `ai-training-db.cluster-ct48ogi24zxp.ap-south-1.docdb.amazonaws.com:27017`.
   - Database: `trainingdb`, Username: `dbuser`, Password: `SiliconValley100%`.
   - Enable SSL.

2. **S3**:
   - Create a bucket (e.g., `ai-training-models`) in `ap-south-1`.

3. **Kafka**:
   - Deploy Kafka in EKS (endpoint: `kafka.default.svc.cluster.local:9092`).

4. **IAM Role (IRSA)**:
   - Create an IAM role (`ai-training-role`) with DocumentDB and S3 permissions.
   - Associate with a Kubernetes service account (`ai-training-sa`).

### 3. Configure the Application
1. **Master Service** (`master-service/src/main/resources/application.properties`):
   ```properties
   spring.data.mongodb.uri=mongodb://dbuser:SiliconValley100%25@ai-training-db.cluster-ct48ogi24zxp.ap-south-1.docdb.amazonaws.com:27017/trainingdb?ssl=true
   spring.kafka.bootstrap-servers=kafka.default.svc.cluster.local:9092
   aws.region=ap-south-1
   management.endpoints.web.exposure.include=health
   management.endpoint.health.show-details=always
   ```

2. **Worker Service** (`worker-service/src/main/resources/application.properties`):
   ```properties
   spring.data.mongodb.uri=mongodb://dbuser:SiliconValley100%25@ai-training-db.cluster-ct48ogi24zxp.ap-south-1.docdb.amazonaws.com:27017/trainingdb?ssl=true
   spring.kafka.bootstrap-servers=kafka.default.svc.cluster.local:9092
   aws.region=ap-south-1
   management.endpoints.web.exposure.include=health
   management.endpoint.health.show-details=always
   ```

### 4. Configure SSL for DocumentDB and S3
1. Download certificates:
   ```bash
   curl -o rds-ap-south-1-bundle.pem https://truststore.pki.rds.amazonaws.com/ap-south-1/ap-south-1-bundle.pem
   curl -o s3-root-ca.pem https://www.amazontrust.com/repository/AmazonRootCA1.pem
   ```
   Split `rds-ap-south-1-bundle.pem` into `ap-south-1-cert-1.pem` (root CA) and `ap-south-1-subordinate-ca.pem` (subordinate CA).

2. Import into JVM’s `cacerts`:
   ```bash
   keytool -import -trustcacerts -file ap-south-1-cert-1.pem -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit -alias rds-ap-south-1-root-ca -noprompt
   keytool -import -trustcacerts -file ap-south-1-subordinate-ca.pem -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit -alias rds-ap-south-1-subordinate-ca -noprompt
   keytool -import -trustcacerts -file s3-root-ca.pem -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit -alias s3-root-ca -noprompt
   ```

### 5. Build Docker Images
1. **Master Service**:
   ```bash
   cd master-service
   mvn clean package
   docker build -t 676206948598.dkr.ecr.ap-south-1.amazonaws.com/master-service:latest .
   docker push 676206948598.dkr.ecr.ap-south-1.amazonaws.com/master-service:latest
   ```

2. **Worker Service**:
   ```bash
   cd worker-service
   mvn clean package
   docker build -t 676206948598.dkr.ecr.ap-south-1.amazonaws.com/worker-service:latest .
   docker push 676206948598.dkr.ecr.ap-south-1.amazonaws.com/worker-service:latest
   ```

---

## Deployment

### 1. Create Kubernetes Resources
1. **ECR Secret**:
   ```bash
   kubectl create secret docker-registry ecr-secret \
     --docker-server=676206948598.dkr.ecr.ap-south-1.amazonaws.com \
     --docker-username=AWS \
     --docker-password=$(aws ecr get-login-password --region ap-south-1)
   ```

2. **Deployments** (`deployments.yaml`):
   ```yaml
   # Master Service Deployment
   apiVersion: apps/v1
   kind: Deployment
   metadata:
     name: master-deployment
     labels:
       app: master
   spec:
     replicas: 1
     selector:
       matchLabels:
         app: master
     template:
       metadata:
         labels:
           app: master
       spec:
         serviceAccountName: ai-training-sa
         imagePullSecrets:
           - name: ecr-secret
         containers:
           - name: master
             image: 676206948598.dkr.ecr.ap-south-1.amazonaws.com/master-service:latest
             ports:
               - containerPort: 8080
             env:
               - name: SPRING_DATA_MONGODB_URI
                 value: "mongodb://dbuser:SiliconValley100%25@ai-training-db.cluster-ct48ogi24zxp.ap-south-1.docdb.amazonaws.com:27017/trainingdb?ssl=true"
               - name: SPRING_KAFKA_BOOTSTRAP_SERVERS
                 value: "kafka.default.svc.cluster.local:9092"
               - name: AWS_REGION
                 value: "ap-south-1"
             resources:
               requests:
                 memory: "512Mi"
                 cpu: "250m"
               limits:
                 memory: "1Gi"
                 cpu: "500m"
             readinessProbe:
               httpGet:
                 path: /actuator/health
                 port: 8080
               initialDelaySeconds: 30
               periodSeconds: 10

   # Worker Service Deployment
   apiVersion: apps/v1
   kind: Deployment
   metadata:
     name: worker-deployment
     labels:
       app: worker
   spec:
     replicas: 3
     selector:
       matchLabels:
         app: worker
     template:
       metadata:
         labels:
           app: worker
       spec:
         serviceAccountName: ai-training-sa
         initContainers:
           - name: wait-for-kafka
             image: busybox
             command: ["sh", "-c", "until nc -z kafka.default.svc.cluster.local 9092; do echo 'Waiting for Kafka...'; sleep 2; done"]
         containers:
           - name: worker
             image: 676206948598.dkr.ecr.ap-south-1.amazonaws.com/worker-service:latest
             env:
               - name: SPRING_DATA_MONGODB_URI
                 value: "mongodb://dbuser:SiliconValley100%25@ai-training-db.cluster-ct48ogi24zxp.ap-south-1.docdb.amazonaws.com:27017/trainingdb?ssl=true"
               - name: SPRING_KAFKA_BOOTSTRAP_SERVERS
                 value: "kafka.default.svc.cluster.local:9092"
               - name: AWS_REGION
                 value: "ap-south-1"
             resources:
               requests:
                 memory: "1Gi"
                 cpu: "500m"
               limits:
                 memory: "2Gi"
                 cpu: "1"
   ```
   Apply:
   ```bash
   kubectl apply -f deployments.yaml
   ```

3. **Service** (`services.yaml`):
   ```yaml
   apiVersion: v1
   kind: Service
   metadata:
     name: master-service
   spec:
     selector:
       app: master
     ports:
       - protocol: TCP
         port: 80
         targetPort: 8080
     type: ClusterIP
   ```
   Apply:
   ```bash
   kubectl apply -f services.yaml
   ```

### 2. Verify Deployment
1. Check pod status:
   ```bash
   kubectl get pods -l app=master
   kubectl get pods -l app=worker
   ```
2. Check deployment status:
   ```bash
   kubectl rollout status deployment master-deployment
   kubectl rollout status deployment worker-deployment
   ```
3. Check logs:
   ```bash
   kubectl logs -l app=master --tail=100
   kubectl logs -l app=worker --tail=100
   ```

---

## Operations

### Scaling
Scale worker nodes:
```bash
kubectl scale deployment worker-deployment --replicas=5
```

### Monitoring
- Health endpoint: `/actuator/health` (master service).
- Use `kubectl logs` and `kubectl describe pod` for debugging.
- Integrate with AWS CloudWatch for logging.

### Troubleshooting
- **Pod Not Ready**: Check `kubectl describe pod` for readiness probe failures.
- **DocumentDB Issues**: Verify URI, credentials, and `cacerts` truststore.
- **S3 Access**: Ensure IAM role permissions and IRSA setup.

### Upgrading
1. Update code in `master-service` or `worker-service`.
2. Rebuild and push images:
   ```bash
   cd master-service
   mvn clean package
   docker build -t 676206948598.dkr.ecr.ap-south-1.amazonaws.com/master-service:latest .
   docker push 676206948598.dkr.ecr.ap-south-1.amazonaws.com/master-service:latest
   ```
   Repeat for `worker-service`.
3. Restart deployments:
   ```bash
   kubectl rollout restart deployment master-deployment
   kubectl rollout restart deployment worker-deployment
   ```

---

## Lessons Learned
- Used JVM’s `cacerts` truststore to simplify SSL configuration.
- Configured Spring Boot Actuator for reliable health checks.
- Debugged SSL issues with `-Djavax.net.debug=ssl:handshake:verbose`.

## Future Improvements
- Add liveness probes for automatic pod restarts.
- Implement CI/CD with GitHub Actions.
- Integrate Prometheus and Grafana for monitoring.

---

## Contact
For questions, contact Aditya Tiwari at adityatiwari@example.com.

---

