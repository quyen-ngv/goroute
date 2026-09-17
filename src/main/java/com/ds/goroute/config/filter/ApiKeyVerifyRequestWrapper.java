package com.ds.goroute.config.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.MediaType;

import java.io.*;

@Setter
@Getter
public class ApiKeyVerifyRequestWrapper extends HttpServletRequestWrapper {

    /**
     * Ceiling on what one request may hold in memory here. This wrapper runs before
     * Spring Security, so an anonymous caller reaches it; without a ceiling a single
     * large POST buffers unbounded. Every upload in this app is multipart and never
     * reaches this class, so the limit only has to cover JSON payloads, which are
     * orders of magnitude smaller.
     */
    public static final int DEFAULT_MAX_BODY_CHARS = 10 * 1024 * 1024;

    private String body;

    public ApiKeyVerifyRequestWrapper(HttpServletRequest request) throws IOException {
        this(request, DEFAULT_MAX_BODY_CHARS);
    }

    public ApiKeyVerifyRequestWrapper(HttpServletRequest request, int maxBodyChars) throws IOException {
        super(request);
        StringBuilder stringBuilder = new StringBuilder();
        BufferedReader bufferedReader = null;
        try {
            InputStream inputStream = request.getInputStream();
            if (inputStream != null) {
                bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                char[] charBuffer = new char[128];
                int bytesRead = -1;
                while ((bytesRead = bufferedReader.read(charBuffer)) > 0) {
                    stringBuilder.append(charBuffer, 0, bytesRead);
                    if (stringBuilder.length() > maxBodyChars) {
                        // Stop reading: the point is not to finish measuring the body,
                        // it is not to hold it.
                        throw new BodyTooLargeException(maxBodyChars);
                    }
                }
            }
        } finally {
            if (bufferedReader != null) {
                bufferedReader.close();
            }
        }
        body = stringBuilder.toString();
    }

    /**
     * Raised when a request body is larger than this filter is willing to buffer. An
     * {@link IOException} so the servlet contract still holds; the filter turns it into
     * the app's normal "too large" response.
     */
    public static class BodyTooLargeException extends IOException {

        private final int limit;

        public BodyTooLargeException(int limit) {
            super("Request body exceeds the " + limit + " character buffer limit");
            this.limit = limit;
        }

        public int limit() {
            return limit;
        }
    }

    @Override
    public ServletInputStream getInputStream() {
        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(body.getBytes());
        return new ServletInputStream() {
            public int read() {
                return byteArrayInputStream.read();
            }

            @Override
            public boolean isFinished() {
                // TODO Auto-generated method stub
                return false;
            }

            @Override
            public boolean isReady() {
                // TODO Auto-generated method stub
                return false;
            }

            @Override
            public void setReadListener(ReadListener listener) {
                // TODO Auto-generated method stub
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(this.getInputStream()));
    }

    @Override
    public String getContentType() {
        String contentType = super.getContentType();
        return contentType == null ? MediaType.APPLICATION_JSON_VALUE : contentType;
    }
}
