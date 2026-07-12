package com.ds.goroute.thirdparty.scrape;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScrapeServiceClient {

    private final RestTemplate restTemplate;

    @Value("${scrape.service.base-url:http://google-maps-bot:8080}")
    private String baseUrl;

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
            log.warn("Scrape resolve failed for url {}: {}", googleMapsUrl, e.getMessage());
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
            log.warn("Place search failed for query {}: {}", query, e.getMessage());
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
            log.error("Scrape trigger failed for url {}: {}", request.getUrl(), e.getMessage());
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
            log.error("Place scrape/import trigger failed for url {}: {}", request.getUrl(), e.getMessage());
            return null;
        }
    }

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
            log.error("Social location trigger failed for url {}: {}", request.getUrl(), e.getMessage());
            return null;
        }
    }

    public ScrapeJobStatusResponse pollJob(String jobId) {
        String url = baseUrl + "/api/v1/jobs/" + jobId;
        try {
            return restTemplate.getForObject(url, ScrapeJobStatusResponse.class);
        } catch (Exception e) {
            log.warn("Scrape poll failed for job {}: {}", jobId, e.getMessage());
            return null;
        }
    }
}
