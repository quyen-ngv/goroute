package com.ds.goroute.service.impl;

import com.ds.goroute.dto.response.PublicPage;
import com.ds.goroute.dto.response.PublicUserProfileResponse;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.service.ContentModerationService;
import com.ds.goroute.service.HiddenImageService;
import com.ds.goroute.service.LocationImageService;
import com.ds.goroute.service.PublicProfileService;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.TripVisibility;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicPageServiceImplTest {

    private static final String BASE_URL = "https://onestudy.id.vn/goroute";

    private PublicProfileService profiles;
    private TripRepository trips;
    private ContentModerationService moderation;
    private LocationImageService locationImages;
    private HiddenImageService hiddenImages;
    private PublicPageServiceImpl service;

    @BeforeEach
    void setUp() {
        profiles = mock(PublicProfileService.class);
        trips = mock(TripRepository.class);
        moderation = mock(ContentModerationService.class);
        locationImages = mock(LocationImageService.class);
        hiddenImages = mock(HiddenImageService.class);
        when(profiles.findGuestProfile(any())).thenReturn(Optional.empty());
        service = new PublicPageServiceImpl(profiles, trips, moderation, locationImages, hiddenImages,
                new ObjectMapper(), BASE_URL + "/");
    }

    @Test
    void aProfilePageCarriesItsOwnPreviewTagsAndHeader() {
        when(profiles.findGuestProfile("an.food")).thenReturn(Optional.of(profile("Ăn cùng An", "Review quán ngon Hà Nội")));

        PublicPage page = service.profilePage("an.food", true);

        assertThat(page.found()).isTrue();
        assertThat(page.html())
                .contains("<html lang=\"vi\">")
                .contains("<title>Ăn cùng An (@an.food) · Vietdetour</title>")
                .contains("<meta property=\"og:title\" content=\"Ăn cùng An (@an.food) · Vietdetour\">")
                .contains("<meta property=\"og:description\" content=\"Review quán ngon Hà Nội\">")
                .contains("<meta property=\"og:image\" content=\"https://cdn/avatar.jpg\">")
                .contains("<link rel=\"canonical\" href=\"" + BASE_URL + "/u/an.food\">")
                .contains("<meta name=\"robots\" content=\"index, follow\">")
                .contains("<h1 class=\"name\">Ăn cùng An</h1>")
                .contains("data-username=\"an.food\" data-found=\"true\"")
                .contains("\"@type\":\"ProfilePage\"")
                .doesNotContain("{{");
    }

    @Test
    void theCanonicalUrlIsTheStoredUsernameWhateverTheLinkSpelledOut() {
        when(profiles.findGuestProfile("@An.Food")).thenReturn(Optional.of(profile("An", null)));

        assertThat(service.profilePage("@An.Food", true).html())
                .contains("<link rel=\"canonical\" href=\"" + BASE_URL + "/u/an.food\">")
                .contains("data-username=\"an.food\"");
    }

    @Test
    void withoutABioThePreviewSaysWhatIsOnTheProfileInTheReadersLanguage() {
        when(profiles.findGuestProfile("an.food")).thenReturn(Optional.of(profile("An", null)));

        assertThat(service.profilePage("an.food", false).html())
                .contains("<html lang=\"en\">")
                .contains("content=\"An on Vietdetour: 12 check-ins, 3 shared trips.\"");
        assertThat(service.profilePage("an.food", true).html())
                .contains("content=\"An trên Vietdetour: 12 check-in, 3 lịch trình đã chia sẻ.\"");
    }

    @Test
    void whatTheUserWroteIsEscapedEverywhereItLands() {
        PublicUserProfileResponse profile = profile("<script>alert(1)</script>",
                "\"><img src=x onerror=alert(1)> </script><script>alert(2)</script> {{HEADER}}");
        when(profiles.findGuestProfile("an.food")).thenReturn(Optional.of(profile));

        String html = service.profilePage("an.food", true).html();

        assertThat(html)
                .doesNotContain("<script>alert")
                .doesNotContain("<img src=x")
                .contains("&lt;script&gt;alert(1)&lt;/script&gt;")
                // The structured data is inside a <script>: no raw "<" may survive there.
                .contains("\\u003c/script\\u003e")
                // A placeholder typed into a bio is text, never expanded.
                .contains("{{HEADER}}");
    }

    @Test
    void aHiddenAvatarNeverReachesThePreview() {
        when(profiles.findGuestProfile("an.food")).thenReturn(Optional.of(profile("An", null)));
        when(hiddenImages.isHidden("https://cdn/avatar.jpg")).thenReturn(true);

        assertThat(service.profilePage("an.food", true).html())
                .doesNotContain("https://cdn/avatar.jpg")
                .contains("<meta property=\"og:image\" content=\"" + PublicPageServiceImpl.BRAND_IMAGE_URL + "\">");
    }

    @Test
    void anUnknownUsernameIsANoindexPageWithNothingPersonal() {
        PublicPage page = service.profilePage("<b>ghost</b>", true);

        assertThat(page.found()).isFalse();
        assertThat(page.html())
                .contains("<meta name=\"robots\" content=\"noindex\">")
                .contains("Không tìm thấy người dùng")
                .contains("data-found=\"false\"")
                .contains("&lt;b&gt;ghost&lt;/b&gt;")
                .doesNotContain("<b>ghost</b>")
                .doesNotContain("{{");
    }

    @Test
    void aPublicTripLinkDescribesTheTrip() {
        Trip trip = trip(TripVisibility.PUBLIC);
        when(trips.findById(trip.getId())).thenReturn(Optional.of(trip));
        when(locationImages.getImageForDestination("Đà Lạt")).thenReturn("https://cdn/dalat.jpg");

        String html = service.tripPage(trip.getId()).html();

        assertThat(html)
                .contains("<title>Đà Lạt 3N2Đ · Vietdetour</title>")
                .contains("<meta property=\"og:image\" content=\"https://cdn/dalat.jpg\">")
                .contains("content=\"Đà Lạt · 3-day itinerary\"")
                .contains("<link rel=\"canonical\" href=\"" + BASE_URL + "/ondetour/" + trip.getId() + "\">")
                .doesNotContain(PublicPageServiceImpl.TRIP_TITLE_TAG);
    }

    @Test
    void aLinkOnlyTripPreviewsButStaysOutOfSearch() {
        Trip shared = trip(TripVisibility.SHARED);
        when(trips.findById(shared.getId())).thenReturn(Optional.of(shared));

        assertThat(service.tripPage(shared.getId()).html())
                .contains("<title>Đà Lạt 3N2Đ · Vietdetour</title>")
                .contains("<meta name=\"robots\" content=\"noindex\">");
    }

    @Test
    void aTripNobodyMayReadByLinkKeepsTheGenericPage() {
        Trip privateTrip = trip(TripVisibility.PRIVATE);
        Trip takenDown = trip(TripVisibility.PUBLIC);
        when(trips.findById(privateTrip.getId())).thenReturn(Optional.of(privateTrip));
        when(trips.findById(takenDown.getId())).thenReturn(Optional.of(takenDown));
        when(moderation.isTakenDown(ModeratedContentType.TRIP, takenDown.getId())).thenReturn(true);

        assertThat(service.tripPage(privateTrip.getId()).html()).contains(PublicPageServiceImpl.TRIP_TITLE_TAG)
                .doesNotContain("Đà Lạt 3N2Đ");
        assertThat(service.tripPage(takenDown.getId()).html()).contains(PublicPageServiceImpl.TRIP_TITLE_TAG)
                .doesNotContain("Đà Lạt 3N2Đ");
        assertThat(service.tripPage(null).html()).contains(PublicPageServiceImpl.TRIP_TITLE_TAG);
    }

    @Test
    void fillLeavesUnknownPlaceholdersAlone() {
        assertThat(PublicPageServiceImpl.fill("a {{X}} b {{Y}} c $1", Map.of("X", "$1\\")))
                .isEqualTo("a $1\\ b {{Y}} c $1");
    }

    private static PublicUserProfileResponse profile(String name, String bio) {
        return PublicUserProfileResponse.builder()
                .id(UUID.randomUUID())
                .username("an.food")
                .fullName(name)
                .bio(bio)
                .avatarUrl("https://cdn/avatar.jpg")
                .checkinCount(12)
                .tripCount(3)
                .followersCount(40)
                .build();
    }

    private static Trip trip(TripVisibility visibility) {
        return Trip.builder()
                .id(UUID.randomUUID())
                .name("Đà Lạt 3N2Đ")
                .destination("Đà Lạt")
                .startDate(LocalDate.of(2026, 11, 1))
                .endDate(LocalDate.of(2026, 11, 3))
                .visibility(visibility)
                .isDeleted(false)
                .build();
    }
}
