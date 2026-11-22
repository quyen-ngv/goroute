package com.ds.goroute.thirdparty.caesium;

import com.ds.goroute.config.CaesiumProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Client of the caesium compression service. {@code POST /compress} takes the raw image and
 * returns it re-encoded as WebP: rotated upright, in sRGB, with no metadata (GPS included),
 * scaled down to the long edge and never up. A non-2xx answer throws a
 * {@link org.springframework.web.client.RestClientException}.
 */
@Component
public class CaesiumClient {

    private final RestClient restClient;
    private final CaesiumProperties properties;

    public CaesiumClient(RestClient.Builder restClientBuilder, CaesiumProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        this.restClient = restClientBuilder.clone()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
        this.properties = properties;
    }

    /** Compresses with the configured quality and long edge. */
    public byte[] compressToWebp(byte[] image) {
        return compressToWebp(image, properties.getQuality(), properties.getMaxLongEdge());
    }

    public byte[] compressToWebp(byte[] image, int quality, int maxLongEdge) {
        byte[] webp = restClient.post()
                .uri(uri -> uri.path("/compress")
                        .queryParam("quality", quality)
                        .queryParam("longEdge", maxLongEdge)
                        .build())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(image)
                .retrieve()
                .body(byte[].class);
        if (webp == null || webp.length == 0) {
            throw new IllegalStateException("Caesium returned an empty image");
        }
        return webp;
    }
}
