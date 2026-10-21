package com.ds.goroute.service.impl;

import com.ds.goroute.dto.response.PublicPage;
import com.ds.goroute.dto.response.PublicUserProfileResponse;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.service.ContentModerationService;
import com.ds.goroute.service.HiddenImageService;
import com.ds.goroute.service.LocationImageService;
import com.ds.goroute.service.PublicPageService;
import com.ds.goroute.service.PublicProfileService;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.TripVisibility;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class PublicPageServiceImpl implements PublicPageService {

    static final String PROFILE_TEMPLATE = "public-pages/profile.html";
    static final String TRIP_TEMPLATE = "static/ondetour.html";

    /** The line in ondetour.html the trip's preview tags replace. */
    static final String TRIP_TITLE_TAG = "<title>Shared Trip - Ondetour</title>";

    /**
     * The app icon, for previews of a profile without an avatar and of a trip without a
     * cover. The same file the admin console uses as its brand logo ({@code BRAND_LOGO_URL}).
     */
    static final String BRAND_IMAGE_URL =
            "https://onestudy.id.vn/resource/vietdetour/admin-media/06a38eef-3fb4-4139-9c5e-3240ea368c66-app_icon.png";

    static final String SITE_NAME = "Vietdetour";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Z_]+)}}");

    /** Long enough for every preview surface; Google cuts around here too. */
    static final int DESCRIPTION_MAX_LENGTH = 200;

    private final PublicProfileService publicProfileService;
    private final TripRepository tripRepository;
    private final ContentModerationService contentModerationService;
    private final LocationImageService locationImageService;
    private final HiddenImageService hiddenImageService;
    private final ObjectMapper objectMapper;
    private final String publicBaseUrl;

    private volatile String profileTemplate;
    private volatile String tripTemplate;

    public PublicPageServiceImpl(PublicProfileService publicProfileService,
                                 TripRepository tripRepository,
                                 ContentModerationService contentModerationService,
                                 LocationImageService locationImageService,
                                 HiddenImageService hiddenImageService,
                                 ObjectMapper objectMapper,
                                 @Value("${goroute.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.publicProfileService = publicProfileService;
        this.tripRepository = tripRepository;
        this.contentModerationService = contentModerationService;
        this.locationImageService = locationImageService;
        this.hiddenImageService = hiddenImageService;
        this.objectMapper = objectMapper;
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
    }

    @Override
    public PublicPage profilePage(String username, boolean vietnamese) {
        String handle = username == null ? "" : username.trim();
        PublicUserProfileResponse profile = publicProfileService.findGuestProfile(handle).orElse(null);
        // The stored username, so /u/An.Food and /u/@an.food are one page to a search engine.
        String canonicalUrl = publicBaseUrl + "/u/" + UriUtils.encodePathSegment(
                profile == null ? handle : profile.getUsername(), StandardCharsets.UTF_8);
        Copy copy = vietnamese ? Copy.VI : Copy.EN;

        Map<String, String> values = new LinkedHashMap<>();
        values.put("LANG", vietnamese ? "vi" : "en");
        values.put("USERNAME", html(profile == null ? handle : profile.getUsername()));
        values.put("FOUND", String.valueOf(profile != null));
        if (profile == null) {
            values.put("TITLE", html(copy.notFoundTitle()));
            values.put("META", metaTags(copy.notFoundTitle(), copy.notFoundDescription(), BRAND_IMAGE_URL,
                    canonicalUrl, "website", false));
            values.put("JSON_LD", "");
            values.put("HEADER", "");
            return new PublicPage(false, fill(profileTemplate(), values));
        }

        String name = displayName(profile);
        String title = copy.profileTitle(name, profile.getUsername());
        String description = truncate(profile.getBio() != null && !profile.getBio().isBlank()
                ? profile.getBio().trim()
                : copy.profileDescription(name, profile.getCheckinCount(), profile.getTripCount()));
        String image = visibleImage(profile.getAvatarUrl());

        values.put("TITLE", html(title));
        values.put("META", metaTags(title, description, image, canonicalUrl, "profile", true));
        values.put("JSON_LD", jsonLd(profile, name, canonicalUrl, image));
        values.put("HEADER", header(profile, name, image, copy));
        return new PublicPage(true, fill(profileTemplate(), values));
    }

    @Override
    public PublicPage tripPage(UUID tripId) {
        String page = tripTemplate();
        Trip trip = tripId == null ? null : publicTrip(tripId);
        if (trip == null) {
            return new PublicPage(true, page);
        }
        if (!page.contains(TRIP_TITLE_TAG)) {
            log.warn("ondetour.html no longer has the title the trip preview tags replace; serving it unchanged");
            return new PublicPage(true, page);
        }
        String canonicalUrl = publicBaseUrl + "/ondetour/" + trip.getId();
        String cover = trip.getCoverImageUrl() == null || trip.getCoverImageUrl().isBlank()
                ? locationImageService.getImageForDestination(trip.getDestination())
                : trip.getCoverImageUrl();
        String title = trip.getName() == null || trip.getName().isBlank()
                ? SITE_NAME
                : trip.getName().trim() + " · " + SITE_NAME;
        String description = truncate(trip.getDescription() != null && !trip.getDescription().isBlank()
                ? trip.getDescription().trim()
                : tripSummary(trip));
        String tags = "<title>" + html(title) + "</title>\n"
                + metaTags(title, description, visibleImage(cover), canonicalUrl, "article", true);
        return new PublicPage(true, page.replace(TRIP_TITLE_TAG, tags));
    }

    /**
     * Only a PUBLIC, live, not taken down trip is described: the preview is read by a
     * crawler with no account, so SHARED (members only) gets the generic tags like PRIVATE.
     */
    private Trip publicTrip(UUID tripId) {
        return tripRepository.findById(tripId)
                .filter(trip -> trip.getVisibility() == TripVisibility.PUBLIC)
                .filter(trip -> !Boolean.TRUE.equals(trip.getIsDeleted()))
                .filter(trip -> !contentModerationService.isTakenDown(ModeratedContentType.TRIP, trip.getId()))
                .orElse(null);
    }

    /** Moderation may have hidden the picture; the API swaps it, so the preview must too. */
    private String visibleImage(String url) {
        if (url == null || url.isBlank() || hiddenImageService.isHidden(url)) {
            return BRAND_IMAGE_URL;
        }
        return url;
    }

    private static String metaTags(String title, String description, String image, String canonicalUrl,
                                   String type, boolean indexable) {
        StringBuilder tags = new StringBuilder();
        tags.append("<meta name=\"description\" content=\"").append(html(description)).append("\">\n");
        tags.append("<meta name=\"robots\" content=\"").append(indexable ? "index, follow" : "noindex").append("\">\n");
        tags.append("<link rel=\"canonical\" href=\"").append(html(canonicalUrl)).append("\">\n");
        tags.append("<meta property=\"og:site_name\" content=\"").append(SITE_NAME).append("\">\n");
        tags.append("<meta property=\"og:type\" content=\"").append(type).append("\">\n");
        tags.append("<meta property=\"og:title\" content=\"").append(html(title)).append("\">\n");
        tags.append("<meta property=\"og:description\" content=\"").append(html(description)).append("\">\n");
        tags.append("<meta property=\"og:url\" content=\"").append(html(canonicalUrl)).append("\">\n");
        tags.append("<meta property=\"og:image\" content=\"").append(html(image)).append("\">\n");
        tags.append("<meta name=\"twitter:card\" content=\"summary\">\n");
        tags.append("<meta name=\"twitter:title\" content=\"").append(html(title)).append("\">\n");
        tags.append("<meta name=\"twitter:description\" content=\"").append(html(description)).append("\">\n");
        tags.append("<meta name=\"twitter:image\" content=\"").append(html(image)).append("\">");
        return tags.toString();
    }

    /** schema.org ProfilePage, so a search result can show the person rather than a page. */
    private String jsonLd(PublicUserProfileResponse profile, String name, String canonicalUrl, String image) {
        Map<String, Object> person = new LinkedHashMap<>();
        person.put("@type", "Person");
        person.put("name", name);
        person.put("alternateName", "@" + profile.getUsername());
        person.put("identifier", profile.getUsername());
        person.put("image", image);
        if (profile.getBio() != null && !profile.getBio().isBlank()) {
            person.put("description", profile.getBio().trim());
        }
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("@context", "https://schema.org");
        page.put("@type", "ProfilePage");
        page.put("url", canonicalUrl);
        page.put("mainEntity", person);
        try {
            // Inside <script>: "</script>" in a bio must not end the element.
            String json = objectMapper.writeValueAsString(page)
                    .replace("<", "\\u003c")
                    .replace(">", "\\u003e")
                    .replace("&", "\\u0026");
            return "<script type=\"application/ld+json\">" + json + "</script>";
        } catch (JsonProcessingException e) {
            log.warn("Could not write the profile's structured data: {}", e.getOriginalMessage());
            return "";
        }
    }

    /**
     * The profile header, rendered here rather than by the script: it is what a search engine
     * indexes and what a reader sees before the posts load.
     */
    private static String header(PublicUserProfileResponse profile, String name, String image, Copy copy) {
        StringBuilder header = new StringBuilder();
        header.append("<img class=\"avatar\" src=\"").append(html(image)).append("\" alt=\"")
                .append(html(name)).append("\" width=\"96\" height=\"96\">\n");
        header.append("<h1 class=\"name\">").append(html(name)).append("</h1>\n");
        header.append("<p class=\"handle\">@").append(html(profile.getUsername())).append("</p>\n");
        if (profile.getBio() != null && !profile.getBio().isBlank()) {
            header.append("<p class=\"bio\">").append(html(profile.getBio().trim())).append("</p>\n");
        }
        header.append("<ul class=\"stats\">")
                .append(stat(profile.getCheckinCount(), copy.checkins()))
                .append(stat(profile.getTripCount(), copy.trips()))
                .append(stat(profile.getFollowersCount(), copy.followers()))
                .append("</ul>");
        return header.toString();
    }

    private static String stat(long value, String label) {
        return "<li><strong>" + value + "</strong><span>" + html(label) + "</span></li>";
    }

    private static String tripSummary(Trip trip) {
        StringBuilder summary = new StringBuilder();
        if (trip.getDestination() != null && !trip.getDestination().isBlank()) {
            summary.append(trip.getDestination().trim());
        }
        if (trip.getStartDate() != null && trip.getEndDate() != null && !trip.getEndDate().isBefore(trip.getStartDate())) {
            long days = ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1;
            if (!summary.isEmpty()) {
                summary.append(" · ");
            }
            summary.append(days).append("-day itinerary");
        }
        if (summary.isEmpty()) {
            summary.append("A trip itinerary shared on ").append(SITE_NAME);
        }
        return summary.toString();
    }

    private static String displayName(PublicUserProfileResponse profile) {
        String fullName = profile.getFullName();
        return fullName == null || fullName.isBlank() ? profile.getUsername() : fullName.trim();
    }

    private static String truncate(String text) {
        String flat = text.replaceAll("\\s+", " ").trim();
        if (flat.length() <= DESCRIPTION_MAX_LENGTH) {
            return flat;
        }
        return flat.substring(0, DESCRIPTION_MAX_LENGTH - 1).trim() + "…";
    }

    private static String html(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value, StandardCharsets.UTF_8.name());
    }

    /**
     * Replaces each {{KEY}} of the template in one pass, so a bio that happens to contain
     * "{{HEADER}}" is printed as text and never expanded. Values arrive escaped for where
     * they land; an unknown key is left as it is.
     */
    static String fill(String template, Map<String, String> values) {
        Matcher placeholder = PLACEHOLDER.matcher(template);
        StringBuilder page = new StringBuilder(template.length() + 2048);
        while (placeholder.find()) {
            String value = values.getOrDefault(placeholder.group(1), placeholder.group());
            placeholder.appendReplacement(page, Matcher.quoteReplacement(value));
        }
        placeholder.appendTail(page);
        return page.toString();
    }

    private String profileTemplate() {
        String template = profileTemplate;
        if (template == null) {
            template = read(PROFILE_TEMPLATE);
            profileTemplate = template;
        }
        return template;
    }

    private String tripTemplate() {
        String template = tripTemplate;
        if (template == null) {
            template = read(TRIP_TEMPLATE);
            tripTemplate = template;
        }
        return template;
    }

    private static String read(String path) {
        try {
            return StreamUtils.copyToString(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing public page template " + path, e);
        }
    }

    /** The few words the server writes itself; the page's script carries the rest. */
    private record Copy(String locale) {
        static final Copy VI = new Copy("vi");
        static final Copy EN = new Copy("en");

        boolean vi() {
            return "vi".equals(locale);
        }

        String profileTitle(String name, String username) {
            return name + " (@" + username + ") · " + SITE_NAME;
        }

        String profileDescription(String name, long checkins, int trips) {
            return vi()
                    ? name + " trên " + SITE_NAME + ": " + checkins + " check-in, " + trips + " lịch trình đã chia sẻ."
                    : name + " on " + SITE_NAME + ": " + checkins + " check-ins, " + trips + " shared trips.";
        }

        String notFoundTitle() {
            return vi() ? "Không tìm thấy người dùng · " + SITE_NAME : "User not found · " + SITE_NAME;
        }

        String notFoundDescription() {
            return vi()
                    ? "Tài khoản này không tồn tại hoặc đã bị xoá."
                    : "This account does not exist or has been deleted.";
        }

        String checkins() {
            return vi() ? "Check-in" : "Check-ins";
        }

        String trips() {
            return vi() ? "Lịch trình" : "Trips";
        }

        String followers() {
            return vi() ? "Người theo dõi" : "Followers";
        }
    }
}
