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
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

        var s3Builder = S3Client.builder()
                .credentialsProvider(provider)
                .region(region);

        if (awsProperties.getEndpoint() != null && !awsProperties.getEndpoint().isBlank()) {
            s3Builder.endpointOverride(java.net.URI.create(awsProperties.getEndpoint()))
                     .forcePathStyle(true);
        }

        this.s3Client = s3Builder.build();

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

    @Override
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return;
        }
        
        try {
            String key = extractObjectKey(fileUrl);
            if (key != null) {
                s3Client.deleteObject(builder -> builder
                        .bucket(awsProperties.getS3BucketName())
                        .key(key));
                log.debug("Deleted S3 object: {}", key);
            }
        } catch (Exception e) {
            log.error("Failed to delete S3 object {}: {}", fileUrl, e.getMessage());
        }
    }

    @Override
    public void deleteFiles(List<String> fileUrls) {
        if (fileUrls == null || fileUrls.isEmpty()) {
            return;
        }
        
        try {
            // Batch delete (up to 1000 objects per request)
            List<String> keys = fileUrls.stream()
                    .map(this::extractObjectKey)
                    .filter(Objects::nonNull)
                    .toList();
            
            if (keys.isEmpty()) {
                return;
            }
            
            // S3 batch delete supports max 1000 objects
            for (int i = 0; i < keys.size(); i += 1000) {
                List<String> batch = keys.subList(i, Math.min(i + 1000, keys.size()));
                
                var objectIdentifiers = batch.stream()
                        .map(key -> software.amazon.awssdk.services.s3.model.ObjectIdentifier.builder()
                                .key(key)
                                .build())
                        .toList();
                
                s3Client.deleteObjects(builder -> builder
                        .bucket(awsProperties.getS3BucketName())
                        .delete(del -> del.objects(objectIdentifiers)));
                
                log.debug("Deleted {} S3 objects", batch.size());
            }
        } catch (Exception e) {
            log.error("Failed to batch delete S3 objects: {}", e.getMessage());
        }
    }

    @Override
    public List<String> listObjectKeys(String prefix) {
        List<String> keys = new ArrayList<>();
        String continuationToken = null;

        do {
            var requestBuilder = ListObjectsV2Request.builder()
                    .bucket(awsProperties.getS3BucketName())
                    .prefix(prefix == null ? "" : prefix);
            if (continuationToken != null) {
                requestBuilder.continuationToken(continuationToken);
            }

            var response = s3Client.listObjectsV2(requestBuilder.build());
            response.contents().forEach(object -> keys.add(object.key()));
            continuationToken = response.nextContinuationToken();
        } while (continuationToken != null);

        return keys;
    }

    @Override
    public void copyObjectKeys(List<String> keys, String targetPrefix) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        String normalizedPrefix = normalizePrefix(targetPrefix);
        if (normalizedPrefix.isBlank()) {
            throw new IllegalArgumentException("targetPrefix is required");
        }

        for (String key : keys.stream().filter(Objects::nonNull).distinct().toList()) {
            try {
                CopyObjectRequest request = CopyObjectRequest.builder()
                        .sourceBucket(awsProperties.getS3BucketName())
                        .sourceKey(key)
                        .destinationBucket(awsProperties.getS3BucketName())
                        .destinationKey(normalizedPrefix + key)
                        .build();
                s3Client.copyObject(request);
            } catch (Exception e) {
                log.error("Failed to backup S3 object key {} to {}: {}", key, normalizedPrefix, e.getMessage());
                throw new RuntimeException("S3 backup failed for key: " + key, e);
            }
        }
        log.info("Backed up {} S3 object keys to {}", keys.size(), normalizedPrefix);
    }

    @Override
    public void deleteObjectKeys(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }

        try {
            List<String> cleanedKeys = keys.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(key -> !key.isEmpty())
                    .distinct()
                    .toList();

            for (int i = 0; i < cleanedKeys.size(); i += 1000) {
                List<String> batch = cleanedKeys.subList(i, Math.min(i + 1000, cleanedKeys.size()));
                var objectIdentifiers = batch.stream()
                        .map(key -> software.amazon.awssdk.services.s3.model.ObjectIdentifier.builder()
                                .key(key)
                                .build())
                        .toList();

                s3Client.deleteObjects(builder -> builder
                        .bucket(awsProperties.getS3BucketName())
                        .delete(del -> del.objects(objectIdentifiers)));

                log.debug("Deleted {} S3 object keys", batch.size());
            }
        } catch (Exception e) {
            log.error("Failed to batch delete S3 object keys: {}", e.getMessage());
        }
    }

    private String normalizePrefix(String prefix) {
        if (prefix == null) {
            return "";
        }
        String normalized = prefix.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }

    /**
     * Extract S3 key from full URL
     * https://onestudy.id.vn/resource/vietdetour/reviews/xxx.webp -> reviews/xxx.webp
     */
    @Override
    public String extractObjectKey(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        try {
            String trimmedUrl = url.trim();
            if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
                return stripStoragePathPrefix(trimmedUrl);
            }

            String cloudfrontDomain = awsProperties.getCloudfrontDomain();
            String host = URI.create(trimmedUrl).getHost();
            String configuredHost = null;
            if (cloudfrontDomain != null && !cloudfrontDomain.isBlank()) {
                String normalizedDomain = cloudfrontDomain.startsWith("http")
                        ? cloudfrontDomain
                        : "https://" + cloudfrontDomain;
                configuredHost = URI.create(normalizedDomain).getHost();
            }
            String configuredEndpointHost = null;
            if (awsProperties.getEndpoint() != null && !awsProperties.getEndpoint().isBlank()) {
                configuredEndpointHost = URI.create(awsProperties.getEndpoint()).getHost();
            }
            boolean isConfiguredCloudfront = cloudfrontDomain != null
                    && !cloudfrontDomain.isBlank()
                    && host != null
                    && configuredHost != null
                    && host.equalsIgnoreCase(configuredHost);
            boolean isConfiguredEndpoint = configuredEndpointHost != null
                    && host != null
                    && host.equalsIgnoreCase(configuredEndpointHost);
            boolean isBucketHost = host != null
                    && host.toLowerCase().startsWith(awsProperties.getS3BucketName().toLowerCase() + ".");
            boolean isLegacyDomain = trimmedUrl.contains("onestudy.id.vn");

            if (!isConfiguredCloudfront && !isConfiguredEndpoint && !isBucketHost && !isLegacyDomain) {
                return null;
            }

            String path = URI.create(trimmedUrl).getPath();
            if (path == null || path.isBlank()) {
                return null;
            }

            String bucketPrefix = "/" + awsProperties.getS3BucketName() + "/";
            String resourceBucketPrefix = "/resource/" + awsProperties.getS3BucketName() + "/";
            if (path.startsWith(resourceBucketPrefix)) {
                return stripStoragePathPrefix(path.substring(resourceBucketPrefix.length()));
            }
            if (path.startsWith(bucketPrefix)) {
                return stripStoragePathPrefix(path.substring(bucketPrefix.length()));
            }

            if (path.startsWith("/resource/goroute/")) {
                return stripStoragePathPrefix(path.substring("/resource/goroute/".length()));
            } else if (path.startsWith("/resource/")) {
                return stripStoragePathPrefix(path.substring("/resource/".length()));
            } else if (path.startsWith("/")) {
                return stripStoragePathPrefix(path.substring(1));
            }

            return stripStoragePathPrefix(path);
        } catch (Exception e) {
            log.warn("Failed to extract key from URL: {}", url);
            return null;
        }
    }

    private String stripStoragePathPrefix(String key) {
        if (key == null || key.isBlank()) {
            return key;
        }
        String normalized = key;
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.startsWith("resource/")) {
            normalized = normalized.substring("resource/".length());
        }
        String bucketPrefix = awsProperties.getS3BucketName() + "/";
        if (normalized.startsWith(bucketPrefix)) {
            return normalized.substring(bucketPrefix.length());
        }
        if (normalized.startsWith("goroute/")) {
            return normalized.substring("goroute/".length());
        }
        return normalized;
    }
}
