package com.ds.goroute.thirdparty.aws;

import com.ds.goroute.config.AwsProperties;
import com.ds.goroute.config.RemoteFileDownloadProperties;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.net.ssl.HttpsURLConnection;

@Slf4j
@Service
@RequiredArgsConstructor
public class AwsService implements StorageService {

    private final AwsProperties awsProperties;
    private final RemoteFileDownloadProperties remoteFileDownloadProperties;
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
        Path temporaryFile = null;
        try {
            DownloadedRemoteFile downloadedFile = downloadRemoteFile(fileUrl, bearerToken);
            temporaryFile = downloadedFile.path();

            PutObjectRequest putOb = PutObjectRequest.builder()
                    .bucket(awsProperties.getS3BucketName())
                    .key(fileName)
                    .contentType(downloadedFile.contentType())
                    .build();

            s3Client.putObject(putOb, RequestBody.fromFile(temporaryFile));
            return getCloudfrontUrl(fileName);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Failed to upload remote file to S3: {}", exception.getClass().getSimpleName());
            throw new RuntimeException("S3 Upload from URL Failed", exception);
        } finally {
            deleteTemporaryFile(temporaryFile);
        }
    }

    private DownloadedRemoteFile downloadRemoteFile(String fileUrl, String bearerToken) throws IOException {
        URI originalUri = RemoteFileUrlPolicy.requirePublicHttps(fileUrl);
        URI currentUri = originalUri;
        long maxBytes = remoteFileDownloadProperties.getMaxBytes().toBytes();

        for (int redirectCount = 0; redirectCount <= remoteFileDownloadProperties.getMaxRedirects(); redirectCount++) {
            HttpsURLConnection connection = openConnection(currentUri, originalUri, bearerToken);
            try {
                int status = connection.getResponseCode();
                if (status >= 300 && status < 400) {
                    if (redirectCount == remoteFileDownloadProperties.getMaxRedirects()) {
                        throw new IllegalArgumentException("Remote file URL redirects too many times");
                    }
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.isBlank()) {
                        throw new IllegalArgumentException("Remote file URL redirect is invalid");
                    }
                    try {
                        currentUri = RemoteFileUrlPolicy.requirePublicHttps(currentUri.resolve(location));
                    } catch (IllegalArgumentException exception) {
                        throw new IllegalArgumentException("Remote file URL redirect is invalid");
                    }
                    continue;
                }
                if (status < 200 || status >= 300) {
                    throw new IllegalArgumentException("Remote file URL returned an unsuccessful response");
                }
                if (connection.getContentLengthLong() > maxBytes) {
                    throw new IllegalArgumentException("Remote file exceeds the configured size limit");
                }
                return copyToTemporaryFile(connection, currentUri, maxBytes);
            } finally {
                connection.disconnect();
            }
        }

        throw new IllegalArgumentException("Remote file URL redirects too many times");
    }

    private HttpsURLConnection openConnection(URI currentUri, URI originalUri, String bearerToken) throws IOException {
        URLConnection rawConnection = currentUri.toURL().openConnection();
        if (!(rawConnection instanceof HttpsURLConnection connection)) {
            throw new IllegalArgumentException("Remote file URL must use HTTPS");
        }

        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(Math.toIntExact(remoteFileDownloadProperties.getConnectTimeout().toMillis()));
        connection.setReadTimeout(Math.toIntExact(remoteFileDownloadProperties.getReadTimeout().toMillis()));
        connection.setRequestProperty("User-Agent", "GoRoute remote media importer");
        if (bearerToken != null
                && !bearerToken.isBlank()
                && RemoteFileUrlPolicy.isSameOrigin(originalUri, currentUri)) {
            connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
        }
        return connection;
    }

    private DownloadedRemoteFile copyToTemporaryFile(
            HttpsURLConnection connection,
            URI sourceUri,
            long maxBytes) throws IOException {
        Path temporaryFile = Files.createTempFile("goroute-remote-upload-", ".download");
        try (InputStream inputStream = connection.getInputStream();
             OutputStream outputStream = Files.newOutputStream(temporaryFile)) {
            copyWithLimit(inputStream, outputStream, maxBytes);
            return new DownloadedRemoteFile(
                    temporaryFile,
                    resolveContentType(connection.getContentType(), sourceUri));
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(temporaryFile);
            throw exception;
        }
    }

    private void copyWithLimit(InputStream inputStream, OutputStream outputStream, long maxBytes) throws IOException {
        byte[] buffer = new byte[8_192];
        long bytesRead = 0;
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            if (read > maxBytes - bytesRead) {
                throw new IllegalArgumentException("Remote file exceeds the configured size limit");
            }
            outputStream.write(buffer, 0, read);
            bytesRead += read;
        }
    }

    private String resolveContentType(String reportedContentType, URI sourceUri) {
        if (reportedContentType != null && !reportedContentType.isBlank()
                && !reportedContentType.equalsIgnoreCase("application/octet-stream")) {
            return reportedContentType.split(";", 2)[0].trim();
        }

        String inferredContentType = URLConnection.guessContentTypeFromName(sourceUri.getPath());
        return inferredContentType == null ? "application/octet-stream" : inferredContentType;
    }

    private void deleteTemporaryFile(Path temporaryFile) {
        if (temporaryFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporaryFile);
        } catch (IOException exception) {
            log.warn("Could not remove temporary remote upload file");
        }
    }

    private record DownloadedRemoteFile(Path path, String contentType) {
    }

    private String getCloudfrontUrl(String fileName) {
        String domain = awsProperties.getCloudfrontDomain();
        if (domain != null && !domain.isEmpty()) {
            if (domain.endsWith("/")) domain = domain.substring(0, domain.length() - 1);
            if (fileName.startsWith("/")) fileName = fileName.substring(1);
            if (domain.startsWith("http://") || domain.startsWith("https://")) {
                return domain + "/" + fileName;
            }
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
            } else {
                // Either a foreign URL we never stored, or our own URL that stopped
                // matching the configured domain. The second case deletes nothing for
                // every image in the product, so it must not pass unrecorded.
                log.warn("No storage key resolved from URL, nothing deleted: {}", fileUrl);
            }
        } catch (Exception e) {
            log.error("Failed to delete S3 object {}: {}", fileUrl, e.getMessage(), e);
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
            
            deleteInBatches(keys, "URL");
        } catch (Exception e) {
            log.error("Failed to batch delete S3 objects: {}", e.getMessage(), e);
        }
    }

    /**
     * Deletes in batches of 1000 and reports what the bucket refused.
     *
     * <p>{@code deleteObjects} answers 200 with a per-object error list rather than
     * throwing, so discarding the response turns a permission or lock failure into a
     * silent success. Nothing downstream can retry — the delete runs after the caller's
     * transaction has committed — so the log line is the only record that an object is
     * still there, and it names the keys.
     */
    private void deleteInBatches(List<String> keys, String source) {
        for (int i = 0; i < keys.size(); i += 1000) {
            List<String> batch = keys.subList(i, Math.min(i + 1000, keys.size()));

            var objectIdentifiers = batch.stream()
                    .map(key -> software.amazon.awssdk.services.s3.model.ObjectIdentifier.builder()
                            .key(key)
                            .build())
                    .toList();

            var response = s3Client.deleteObjects(builder -> builder
                    .bucket(awsProperties.getS3BucketName())
                    .delete(del -> del.objects(objectIdentifiers)));

            if (response.hasErrors() && !response.errors().isEmpty()) {
                response.errors().forEach(error -> log.error(
                        "S3 refused to delete object. bucket={}, key={}, code={}, message={}",
                        awsProperties.getS3BucketName(), error.key(), error.code(), error.message()));
                log.error("Deleted {} of {} S3 objects in this batch ({} source)",
                        batch.size() - response.errors().size(), batch.size(), source);
            } else {
                log.debug("Deleted {} S3 objects", batch.size());
            }
        }
    }

    @Override
    public List<StorageService.StoredObject> listObjects(String prefix) {
        List<StorageService.StoredObject> objects = new ArrayList<>();
        String continuationToken = null;

        do {
            var requestBuilder = ListObjectsV2Request.builder()
                    .bucket(awsProperties.getS3BucketName())
                    .prefix(prefix == null ? "" : prefix);
            if (continuationToken != null) {
                requestBuilder.continuationToken(continuationToken);
            }

            var response = s3Client.listObjectsV2(requestBuilder.build());
            response.contents().forEach(object ->
                    objects.add(new StorageService.StoredObject(object.key(), object.lastModified())));
            continuationToken = response.nextContinuationToken();
        } while (continuationToken != null);

        return objects;
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
                log.error("Failed to backup S3 object key {} to {}: {}", key, normalizedPrefix, e.getMessage(), e);
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

            deleteInBatches(cleanedKeys, "key");
        } catch (Exception e) {
            log.error("Failed to batch delete S3 object keys: {}", e.getMessage(), e);
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
