# Build stage
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# Install Maven
ENV MAVEN_VERSION=3.9.9
RUN apt-get update && apt-get install -y --no-install-recommends curl tar && \
    mkdir -p /usr/share/maven && \
    curl -fsSL https://archive.apache.org/dist/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz | tar -xzC /usr/share/maven --strip-components=1 && \
    ln -s /usr/share/maven/bin/mvn /usr/bin/mvn

# Copy source and build
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

# Biến môi trường quan trọng giúp tránh lỗi SSL/Networking khi khởi tạo
ENV JAVA_OPTS="-Djava.net.preferIPv4Stack=true -Djava.security.egd=file:/dev/./urandom"

USER spring

EXPOSE 8080

# Chạy ứng dụng với JAVA_OPTS
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]