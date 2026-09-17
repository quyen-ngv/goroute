package com.ds.goroute.thirdparty.scrape;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

@Component
@Slf4j
public class ScrapeServiceClient {

    private final RestTemplate restTemplate;

    public ScrapeServiceClient(@Qualifier("scrapeRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

@Value("${scrape.service.base-url:http://google-maps-bot:8080}")
private String baseUrl;

@Value("${scrape.service.api-key:}")
private String apiKey;

    public ScrapeResolveResponse resolveUrl(String googleMapsUrl) {
        // Pass a URI, not an already encoded String, so RestTemplate does not
        // encode the nested Google Maps query parameters a second time.
        URI url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/api/v1/places/resolve")
                .queryParam("url", googleMapsUrl)
                .build()
                .encode()
                .toUri();
        try {
            ResponseEntity<ScrapeResolveResponse> response =
                    restTemplate.getForEntity(url, ScrapeResolveResponse.class);
            return response.getBody();
        } catch (Exception e) {
            log.warn("Scrape resolve failed for url {}: {}", googleMapsUrl, e.getMessage(), e);
            return null;
        }
    }

    public ScrapePlaceSearchResponse searchPlaces(String query, int limit) {
        URI url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/api/v1/places/search")
                .queryParam("query", query)
                .queryParam("limit", limit)
                .build()
                .encode()
                .toUri();
        try {
            ResponseEntity<ScrapePlaceSearchResponse> response =
                    restTemplate.getForEntity(url, ScrapePlaceSearchResponse.class);
            return response.getBody();
        } catch (Exception e) {
            log.warn("Place search failed for query {}: {}", query, e.getMessage(), e);
            return null;
        }
    }

    public ScrapeJobTriggerResponse triggerContributionScrape(ScrapeContributionJobRequest request) {
        String url = baseUrl + "/api/v1/places/scrape-and-import";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ScrapeContributionJobRequest> entity = new HttpEntity<>(request, headers);
        try {
            ResponseEntity<ScrapeJobTriggerResponse> response =
                    restTemplate.postForEntity(url, entity, ScrapeJobTriggerResponse.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Scrape trigger failed for url {}: {}", request.getUrl(), e.getMessage(), e);
            return null;
        }
    }

    public ScrapeJobTriggerResponse triggerPlaceScrapeImport(ScrapePlaceImportJobRequest request) {
        String url = baseUrl + "/api/v1/places/scrape-and-import";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ScrapePlaceImportJobRequest> entity = new HttpEntity<>(request, headers);
        try {
            ResponseEntity<ScrapeJobTriggerResponse> response =
                    restTemplate.postForEntity(url, entity, ScrapeJobTriggerResponse.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Place scrape/import trigger failed for url {}: {}", request.getUrl(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * Submits a social-location job and says which kind of failure it hit, because the caller
     * pays for what the worker runs and so must know whether the request ever arrived.
     *
     * <p>Returns null only when the request provably never reached the worker (connection
     * refused, unknown host, connect timeout) or when the worker answered with an error status:
     * nothing is running, and the caller may re-send. Throws {@link TriggerUnverifiedException}
     * when the request may have been accepted and only the answer was lost (read timeout, a
     * connection dropped mid-request): re-sending it could pay for the same extraction twice.
     */
    public ScrapeSocialLocationJobResponse triggerSocialLocationJob(ScrapeSocialLocationJobRequest request) {
        String url = baseUrl + "/api/v1/social-location/jobs";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ScrapeSocialLocationJobRequest> entity = new HttpEntity<>(request, headers);
        try {
            ResponseEntity<ScrapeSocialLocationJobResponse> response =
                    restTemplate.postForEntity(url, entity, ScrapeSocialLocationJobResponse.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Social location trigger failed for url {}: {}", request.getUrl(), e.getMessage(), e);
            if (neverReachedWorker(e)) {
                return null;
            }
            throw new TriggerUnverifiedException(describeFailure(e), e);
        }
    }

    /**
     * The trigger failed in a way that cannot rule out the worker having accepted the job, so the
     * caller must verify the outcome instead of re-sending the request.
     */
    public static class TriggerUnverifiedException extends RuntimeException {
        public TriggerUnverifiedException(String reason, Throwable cause) {
            super(reason, cause);
        }
    }

    /**
     * True when the request demonstrably never got as far as the worker's handler: the transport
     * failed while connecting, or the worker answered with an HTTP error status instead of a job
     * id. Everything else -- a read timeout above all, which is what a worker that accepted the
     * job and is still busy looks like -- is treated as "may have been accepted".
     */
    private boolean neverReachedWorker(Throwable error) {
        if (error instanceof RestClientResponseException) {
            // The worker replied, with a status rather than a job id: it started nothing.
            return true;
        }
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.net.UnknownHostException
                    || cause instanceof java.net.NoRouteToHostException
                    || cause instanceof java.net.PortUnreachableException
                    // ConnectException also covers the client libraries' connect-timeout subclasses.
                    || cause instanceof java.net.ConnectException) {
                return true;
            }
            if (cause instanceof java.net.SocketTimeoutException) {
                // HttpURLConnection reports both phases with this one type and tells them apart
                // only by message: "connect timed out" vs "Read timed out".
                String message = cause.getMessage();
                return message != null && message.toLowerCase(java.util.Locale.ROOT).contains("connect");
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    private String describeFailure(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }

    public ScrapeJobStatusResponse pollJob(String jobId) {
        String url = baseUrl + "/api/v1/jobs/" + jobId;
        try {
            return restTemplate.getForObject(url, ScrapeJobStatusResponse.class);
        } catch (Exception e) {
            log.warn("Scrape poll failed for job {}: {}", jobId, e.getMessage(), e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> pollJobData(String jobId) {
        String url = baseUrl + "/api/v1/jobs/" + jobId;
        try {
            return restTemplate.getForObject(url, Map.class);
        } catch (Exception e) {
            log.warn("Scrape job data poll failed for job {}: {}", jobId, e.getMessage(), e);
            return null;
        }
    }

    public ScrapeJobTriggerResponse triggerNationwideJob(ScrapeNationwideJobRequest request) {
        String url = baseUrl + "/api/v1/nationwide/jobs";
        try {
            return restTemplate.postForObject(url, request, ScrapeJobTriggerResponse.class);
        } catch (Exception e) {
            log.error("Nationwide scrape trigger failed: {}", e.getMessage(), e);
            return null;
        }
    }

    public ScrapeJobTriggerResponse triggerPlaceDetailRefreshJob(ScrapePlaceDetailRefreshJobRequest request) {
        String url = baseUrl + "/api/v1/maintenance/places/refresh-details";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (apiKey != null && !apiKey.isBlank()) {
                headers.set("X-API-Key", apiKey);
            }
            ResponseEntity<ScrapeJobTriggerResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    ScrapeJobTriggerResponse.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Place detail refresh trigger failed: {}", e.getMessage(), e);
            return null;
        }
    }

    public ScrapeJobTriggerResponse triggerPlaceReviewRefreshJob(ScrapePlaceReviewRefreshJobRequest request) {
        String url = baseUrl + "/api/v1/maintenance/places/refresh-reviews";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (apiKey != null && !apiKey.isBlank()) {
                headers.set("X-API-Key", apiKey);
            }
            ResponseEntity<ScrapeJobTriggerResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    ScrapeJobTriggerResponse.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Place review refresh trigger failed: {}", e.getMessage(), e);
            return null;
        }
    }

    public ScrapeJobTriggerResponse rerunPlaceReviewRefreshJob(String jobId, String mode) {
        String url = baseUrl + "/api/v1/maintenance/places/refresh-reviews/" + jobId + "/rerun";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (apiKey != null && !apiKey.isBlank()) {
                headers.set("X-API-Key", apiKey);
            }
            ResponseEntity<ScrapeJobTriggerResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of("mode", mode), headers),
                    ScrapeJobTriggerResponse.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Place review refresh rerun failed for job {}: {}", jobId, e.getMessage(), e);
            return null;
        }
    }

    public boolean cancelJob(String jobId) {
        String url = baseUrl + "/api/v1/jobs/" + jobId + "/cancel";
        try {
            restTemplate.postForEntity(url, Map.of(), Void.class);
            return true;
        } catch (Exception e) {
            log.warn("Scrape job cancel failed for {}: {}", jobId, e.getMessage(), e);
            return false;
        }
    }
}
