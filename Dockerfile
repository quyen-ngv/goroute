# Build stage
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Copy pom.xml trước để cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code
COPY . .
RUN mvn clean package -DskipTests

# Sử dụng Ubuntu Jammy (22.04) - Môi trường khuyến nghị của MS
FROM eclipse-temurin:21-jre-jammy

# Cài đặt danh sách đầy đủ các dependencies cho Azure Speech SDK + yt-dlp
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    libssl3 \
    libasound2 \
    libcurl4 \
    libgomp1 \
    libuuid1 \
    libxml2 \
    # Bổ sung các gói đảm bảo native libs có thể link được
    libstdc++6 \
    # FFmpeg - For audio format conversion (MP4 -> WAV)
    ffmpeg \
    # Python3 and pip for yt-dlp
    python3 \
    python3-pip \
    && rm -rf /var/lib/apt/lists/*

# Install yt-dlp
RUN pip3 install --no-cache-dir yt-dlp && \
    yt-dlp --version

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
ENV JAVA_OPTS="-Djava.net.preferIPv4Stack=true -Djava.security.egd=file:/dev/./urandom"
ENV FIREBASE_CREDENTIAL_PATH=/app/goroute-credentials.json

USER spring

EXPOSE 8080

# Chạy ứng dụng với JAVA_OPTS
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]