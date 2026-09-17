package com.ds.goroute.service;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;

public interface StorageService {
    String uploadFile(String fileName, InputStream inputStream, String contentType, long contentLength);
    String uploadFileFromUrl(String fileUrl, String fileName);
    String uploadFileFromUrl(String fileUrl, String fileName, String bearerToken);
    String uploadBytes(byte[] data, String fileName, String contentType);
    void deleteFile(String fileUrl);
    void deleteFiles(List<String> fileUrls);
    /** Objects under a prefix, each with the time it was written. */
    List<StoredObject> listObjects(String prefix);
    void copyObjectKeys(List<String> keys, String targetPrefix);
    void deleteObjectKeys(List<String> keys);
    String extractObjectKey(String fileUrl);

    /** One stored object: where it lives and when it was last written. */
    record StoredObject(String key, Instant lastModified) {
    }
}
