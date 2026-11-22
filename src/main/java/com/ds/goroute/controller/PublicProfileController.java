package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.PublicPage;
import com.ds.goroute.dto.response.PublicProfileCheckinResponse;
import com.ds.goroute.dto.response.PublicProfileTripResponse;
import com.ds.goroute.dto.response.PublicUserProfileResponse;
import com.ds.goroute.service.PublicPageService;
import com.ds.goroute.service.PublicProfileService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The link a person shares to their profile, {@code https://<host>/goroute/u/{username}}:
 * the web page itself and the three reads behind it. Every route is open to guests.
 */
@RestController
@Validated
@RequiredArgsConstructor
public class PublicProfileController extends BaseController {

    /** A page of cards; the web page asks for 12, the cap only stops scraping in one call. */
    static final int MAX_PAGE_SIZE = 50;

    /** Longer than any username the app accepts (50); anything past it cannot exist. */
    static final int MAX_USERNAME_LENGTH = 100;

    private final PublicProfileService publicProfileService;
    private final PublicPageService publicPageService;

    /** The profile header. The app resolves a shared link to a user id through this. */
    @GetMapping("/v1/api/public/users/{username}")
    public ResponseEntity<BaseResponse<PublicUserProfileResponse>> getProfile(
            @PathVariable @Size(min = 1, max = MAX_USERNAME_LENGTH) String username,
            @CurrentUser(required = false) UUID viewerId) {
        return ResponseEntity.ok(ofSucceeded(publicProfileService.getProfile(username, viewerId)));
    }

    @GetMapping("/v1/api/public/users/{username}/checkins")
    public ResponseEntity<BaseResponse<List<PublicProfileCheckinResponse>>> getCheckins(
            @PathVariable @Size(min = 1, max = MAX_USERNAME_LENGTH) String username,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return ResponseEntity.ok(ofSucceeded(publicProfileService.getCheckins(username, page, size)));
    }

    @GetMapping("/v1/api/public/users/{username}/trips")
    public ResponseEntity<BaseResponse<List<PublicProfileTripResponse>>> getTrips(
            @PathVariable @Size(min = 1, max = MAX_USERNAME_LENGTH) String username,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return ResponseEntity.ok(ofSucceeded(publicProfileService.getTrips(username, page, size)));
    }

    /**
     * The web page. An unknown username still gets a page (saying so) but with 404, so a
     * search engine drops it and a link preview shows nothing personal.
     */
    @GetMapping(value = "/u/{username}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getProfilePage(
            @PathVariable @Size(min = 1, max = MAX_USERNAME_LENGTH) String username,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        PublicPage page = publicPageService.profilePage(username, prefersVietnamese(acceptLanguage));
        return ResponseEntity.status(page.found() ? HttpStatus.OK : HttpStatus.NOT_FOUND)
                .contentType(MediaType.TEXT_HTML)
                .body(page.html());
    }

    /**
     * Vietnamese unless the browser's first choice is another language. Link-preview
     * crawlers usually send no header, and the people sharing these links are Vietnamese.
     */
    static boolean prefersVietnamese(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return true;
        }
        String first = acceptLanguage.split(",")[0].trim().toLowerCase(java.util.Locale.ROOT);
        return first.isEmpty() || first.startsWith("vi") || first.startsWith("*");
    }
}
