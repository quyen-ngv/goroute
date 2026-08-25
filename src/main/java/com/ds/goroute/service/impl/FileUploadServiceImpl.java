package com.ds.goroute.service.impl;

import com.ds.goroute.config.FileUploadProperties;
import com.ds.goroute.config.ImgpressProperties;
import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.service.FileUploadService;
import com.ds.goroute.service.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class FileUploadServiceImpl implements FileUploadService {

    private final StorageService storageService;
    private final RestTemplate restTemplate;
    private final FileUploadProperties uploadProperties;
    private final ImgpressProperties imgpressProperties;

    public FileUploadServiceImpl(
            StorageService storageService,
            @Qualifier("scrapeRestTemplate") RestTemplate restTemplate,
            FileUploadProperties uploadProperties,
            ImgpressProperties imgpressProperties) {
        this.storageService = storageService;
        this.restTemplate = restTemplate;
        this.uploadProperties = uploadProperties;
        this.imgpressProperties = imgpressProperties;
    }

    @Override
    public String uploadImage(UUID userId, MultipartFile file) {
        ValidatedImage image = validate(file);
        ValidatedImage upload = compressOrOriginal(image);
        String objectKey = "expenses/" + userId + "/" + UUID.randomUUID() + upload.extension();
        return storageService.uploadFile(
                objectKey,
                new ByteArrayInputStream(upload.bytes()),
                upload.contentType(),
                upload.bytes().length);
    }

    @Override
    public List<String> uploadImages(UUID userId, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw invalid("At least one image is required");
        }
        if (files.size() > uploadProperties.getMaxBatchFiles()) {
            throw invalid("At most " + uploadProperties.getMaxBatchFiles() + " images are allowed");
        }

        // Validate the entire batch before any object is written.
        List<ValidatedImage> validated = files.stream().map(this::validate).toList();
        List<String> urls = new ArrayList<>(validated.size());
        for (ValidatedImage image : validated) {
            ValidatedImage upload = compressOrOriginal(image);
            String objectKey = "expenses/" + userId + "/" + UUID.randomUUID() + upload.extension();
            urls.add(storageService.uploadFile(
                    objectKey,
                    new ByteArrayInputStream(upload.bytes()),
                    upload.contentType(),
                    upload.bytes().length));
        }
        return urls;
    }

    @Override
    public String uploadVideo(UUID userId, MultipartFile file) {
        ValidatedVideo video = validateVideo(file);
        String objectKey = "trip-memory-videos/" + userId + "/" + UUID.randomUUID() + video.extension();
        try (InputStream inputStream = file.getInputStream()) {
            return storageService.uploadFile(
                    objectKey,
                    inputStream,
                    video.contentType(),
                    file.getSize());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read uploaded video", exception);
        }
    }

    private ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalid("Image is empty");
        }
        if (file.getSize() > uploadProperties.getMaxImageSize().toBytes()) {
            throw invalid("Image exceeds the configured size limit");
        }

        String contentType = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase(Locale.ROOT);
        if ("image/jpg".equals(contentType)) {
            contentType = MediaType.IMAGE_JPEG_VALUE;
        }
        if (!uploadProperties.getAllowedImageTypes().contains(contentType)) {
            throw invalid("Only JPEG, PNG, and WEBP images are allowed");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read uploaded image", exception);
        }
        if (!matchesContent(contentType, bytes)) {
            throw invalid("Image content does not match its declared type");
        }
        return new ValidatedImage(bytes, contentType, extension(contentType), safeFilename(file.getOriginalFilename()));
    }

    private ValidatedVideo validateVideo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalid("Video is empty");
        }
        if (file.getSize() > uploadProperties.getMaxVideoSize().toBytes()) {
            throw invalid("Video exceeds the configured size limit");
        }

        String contentType = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase(Locale.ROOT);
        if (!uploadProperties.getAllowedVideoTypes().contains(contentType)) {
            throw invalid("Only MP4, MOV, and WEBM videos are allowed");
        }

        byte[] header;
        try (InputStream inputStream = file.getInputStream()) {
            header = inputStream.readNBytes(12);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read uploaded video", exception);
        }
        if (!matchesVideoContent(contentType, header)) {
            throw invalid("Video content does not match its declared type");
        }
        return new ValidatedVideo(contentType, videoExtension(contentType));
    }

    private ValidatedImage compressOrOriginal(ValidatedImage original) {
        try {
            byte[] compressed = compress(original.bytes(), original.filename());
            if (compressed.length > 0 && compressed.length <= uploadProperties.getMaxImageSize().toBytes()) {
                return new ValidatedImage(compressed, "image/webp", ".webp", original.filename());
            }
        } catch (RuntimeException exception) {
            log.warn("Image compression unavailable; storing validated original image");
        }
        return original;
    }

    private byte[] compress(byte[] bytes, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        });

        String url = UriComponentsBuilder.fromHttpUrl(imgpressProperties.getUrl())
                .path("/compress/one")
                .queryParam("format", "webp")
                .queryParam("quality", imgpressProperties.getQuality())
                .queryParam("width", imgpressProperties.getWidth())
                .queryParam("autoOrient", true)
                .toUriString();
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<>() {});
        Map<String, Object> responseBody = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful()
                || responseBody == null
                || Boolean.TRUE.equals(responseBody.get("error"))
                || !(responseBody.get("data") instanceof String encoded)) {
            throw new IllegalStateException("Image compression failed");
        }
        long maximumEncodedLength = (uploadProperties.getMaxImageSize().toBytes() * 4 / 3) + 4;
        if (encoded.length() > maximumEncodedLength) {
            throw new IllegalStateException("Compressed image exceeds the configured size limit");
        }
        return Base64.getDecoder().decode(encoded);
    }

    private boolean matchesContent(String contentType, byte[] bytes) {
        return switch (contentType) {
            case "image/jpeg" -> bytes.length >= 3
                    && unsigned(bytes[0]) == 0xFF && unsigned(bytes[1]) == 0xD8 && unsigned(bytes[2]) == 0xFF;
            case "image/png" -> bytes.length >= 8
                    && unsigned(bytes[0]) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47
                    && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A;
            case "image/webp" -> bytes.length >= 12
                    && ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP");
            default -> false;
        };
    }

    private boolean matchesVideoContent(String contentType, byte[] bytes) {
        return switch (contentType) {
            case "video/mp4", "video/quicktime" -> bytes.length >= 8 && ascii(bytes, 4, "ftyp");
            case "video/webm" -> bytes.length >= 4
                    && unsigned(bytes[0]) == 0x1A
                    && unsigned(bytes[1]) == 0x45
                    && unsigned(bytes[2]) == 0xDF
                    && unsigned(bytes[3]) == 0xA3;
            default -> false;
        };
    }

    private boolean ascii(byte[] bytes, int offset, String expected) {
        for (int index = 0; index < expected.length(); index++) {
            if (bytes[offset + index] != expected.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private int unsigned(byte value) {
        return value & 0xFF;
    }

    private String extension(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    private String videoExtension(String contentType) {
        return switch (contentType) {
            case "video/quicktime" -> ".mov";
            case "video/webm" -> ".webm";
            default -> ".mp4";
        };
    }

    private String safeFilename(String filename) {
        return filename == null || filename.isBlank() ? "image" : filename.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorConstant.INVALID_PARAMETERS, message);
    }

    private record ValidatedImage(byte[] bytes, String contentType, String extension, String filename) {
    }

    private record ValidatedVideo(String contentType, String extension) {
    }
}
