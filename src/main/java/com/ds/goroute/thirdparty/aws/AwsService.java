package com.ds.goroute.thirdparty.aws;

import com.ds.goroute.config.AwsProperties;
import com.ds.goroute.service.StorageService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.polly.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import java.io.InputStream;
import java.net.URL;

@Slf4j
@Service
@RequiredArgsConstructor
public class AwsService implements StorageService {

    private final AwsProperties awsProperties;
    private S3Client s3Client;
    private PollyClient pollyClient;

    @PostConstruct
    public void init() {
        var credentials = AwsBasicCredentials.create(
                awsProperties.getAccessKeyId(),
                awsProperties.getSecretAccessKey()
        );
        var provider = StaticCredentialsProvider.create(credentials);
        var region = Region.of(awsProperties.getRegion());

        this.s3Client = S3Client.builder()
                .credentialsProvider(provider)
                .region(region)
                .build();

        this.pollyClient = PollyClient.builder()
                .credentialsProvider(provider)
                .region(region)
                .build();
    }

    @Override
    public String uploadFile(String fileName, InputStream inputStream, String contentType, long contentLength) {
        try {
            PutObjectRequest putOb = PutObjectRequest.builder()
                    .bucket(awsProperties.getS3BucketName())
                    .key(fileName)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(putOb, RequestBody.fromInputStream(inputStream, contentLength));

            return getCloudfrontUrl(fileName);

        } catch (Exception e) {
            log.error("Failed to upload to S3: {}", fileName, e);
            throw new RuntimeException("S3 Upload Failed", e);
        }
    }

    @Override
    public String uploadBytes(byte[] data, String fileName, String contentType) {
        try {
            PutObjectRequest putOb = PutObjectRequest.builder()
                    .bucket(awsProperties.getS3BucketName())
                    .key(fileName)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(putOb, RequestBody.fromBytes(data));

            return getCloudfrontUrl(fileName);

        } catch (Exception e) {
            log.error("Failed to upload bytes to S3: {}", fileName, e);
            throw new RuntimeException("S3 Upload Failed", e);
        }
    }

    @Override
    public String uploadFileFromUrl(String fileUrl, String fileName) {
        return uploadFileFromUrl(fileUrl, fileName, null);
    }

    // Helper overloaded method (we should add this to interface if we want it public, 
    // but for now I'll cast inside AiServiceImpl or modify interface. 
    // Ideally, modify Interface first.
    public String uploadFileFromUrl(String fileUrl, String fileName, String bearerToken) {
        try {
            java.net.URLConnection connection = new URL(fileUrl).openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");

            if (bearerToken != null && !bearerToken.isBlank()) {
                connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
            }

            try (InputStream in = connection.getInputStream()) {
                byte[] bytes = in.readAllBytes();

                String contentType = connection.getContentType();
                // If content type is null or looks like generic binary, try to infer from filename/extension
                if (contentType == null || contentType.equalsIgnoreCase("application/octet-stream")) {
                    String lowerName = fileName.toLowerCase();
                    if (lowerName.endsWith(".mp3")) {
                        contentType = "audio/mpeg";
                    } else if (lowerName.endsWith(".wav")) {
                        contentType = "audio/wav";
                    } else if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
                        contentType = "image/jpeg";
                    } else if (lowerName.endsWith(".png")) {
                        contentType = "image/png";
                    } else {
                        contentType = "application/octet-stream";
                    }
                }

                PutObjectRequest putOb = PutObjectRequest.builder()
                        .bucket(awsProperties.getS3BucketName())
                        .key(fileName)
                        .contentType(contentType)
                        .build();

                s3Client.putObject(putOb, RequestBody.fromBytes(bytes));

                return getCloudfrontUrl(fileName);
            }
        } catch (Exception e) {
            log.error("Failed to upload file from URL to S3: {}", fileUrl, e);
            throw new RuntimeException("S3 Upload from URL Failed", e);
        }
    }

    private String getCloudfrontUrl(String fileName) {
        String domain = awsProperties.getCloudfrontDomain();
        if (domain != null && !domain.isEmpty()) {
            // Ensure no double slashes if domain ends with / or filename starts with /
            if (domain.endsWith("/")) domain = domain.substring(0, domain.length() - 1);
            if (fileName.startsWith("/")) fileName = fileName.substring(1);
            return "https://" + domain + "/" + fileName;
        }
        // Fallback to S3 URL if Cloudfront not configured
        return s3Client.utilities().getUrl(GetUrlRequest.builder()
                .bucket(awsProperties.getS3BucketName())
                .key(fileName)
                .build()).toExternalForm();
    }
}
