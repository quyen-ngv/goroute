package com.ds.goroute.service;

import java.io.InputStream;

public interface StorageService {
    String uploadFile(String fileName, InputStream inputStream, String contentType, long contentLength);
    String uploadFileFromUrl(String fileUrl, String fileName);
    String uploadFileFromUrl(String fileUrl, String fileName, String bearerToken);
    String uploadBytes(byte[] data, String fileName, String contentType);
}
