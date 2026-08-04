# Build stage
FROM maven:3.9.9-eclipse-temurin-21 AS builder
ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8"
WORKDIR /app

# Copy pom.xml trước để cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code
COPY . .
RUN mvn clean package -DskipTests

# Runtime image for the Spring Boot application
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Tạo thư mục log và data directories
RUN mkdir -p /app/logs /app/data/lucene-index /app/data/subtitles && \
    chmod -R 777 /app/logs /app/data

# Thêm app user
RUN groupadd --system spring && useradd --system -g spring spring && \
    chown -R spring:spring /app

COPY --from=builder --chown=spring:spring /app/target/ticketmaster-0.0.1-SNAPSHOT.jar app.jar

# Copy Firebase credentials
COPY --from=builder --chown=spring:spring /app/src/main/resources/firebase-credentials.json /app/goroute-credentials.json

# Biến môi trường quan trọng giúp tránh lỗi SSL/Networking khi khởi tạo
ENV JAVA_OPTS="-Djava.net.preferIPv4Stack=true -Djava.security.egd=file:/dev/./urandom -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
ENV FIREBASE_CREDENTIAL_PATH=/app/goroute-credentials.json

USER spring

EXPOSE 8080

# Chạy ứng dụng với JAVA_OPTS
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
