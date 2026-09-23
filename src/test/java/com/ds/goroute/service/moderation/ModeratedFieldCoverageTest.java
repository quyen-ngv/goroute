package com.ds.goroute.service.moderation;

import com.ds.goroute.annotations.ModeratedText;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The guard MOD-03 asks for: adding a new text field that users can write into must not
 * be able to silently skip the shared filter.
 *
 * <p>Every request field whose name looks like free text is either annotated with
 * {@link ModeratedText} or listed in {@link #EXEMPT} with a reason. Being forced to write
 * the reason down is the point -- an exemption then reads as a decision somebody took,
 * not as something everybody forgot.
 */
class ModeratedFieldCoverageTest {

    private static final String REQUEST_PACKAGE = "com.ds.goroute.dto.request";

    /** Field names that carry prose a person typed. */
    private static final Set<String> FREE_TEXT_NAMES = Set.of(
            "text", "content", "caption", "description", "notes", "note", "name", "fullname",
            "displayname", "bio", "title", "address", "message", "comment", "summary", "slogan",
            "tripdescription");

    /**
     * Deliberate exemptions. The key is {@code SimpleClassName.fieldName}; the value is the
     * reason, which is the part that matters.
     */
    private static final Map<String, String> EXEMPT = Map.ofEntries(
            // Operator-only text, never shown to another user.
            Map.entry("AdminPushNotificationRequest.title", "Operator composes it; it is the moderation-side channel"),
            Map.entry("AdminSinglePushNotificationRequest.title", "Operator composes it"),
            Map.entry("AdminUserRequest.fullName", "Operator provisions the account"),
            Map.entry("AdminProvisionPartnerRequest.fullName", "Operator provisions the account"),
            Map.entry("ProvisionPartnerMemberRequest.fullName", "Operator provisions the account"),
            Map.entry("UpsertAppConfigRequest.description", "Operator note on a configuration key"),
            Map.entry("UpsertBookSkeletonRequest.name", "Operator names a layout template"),
            Map.entry("ModeratePlaceImportMappingRequest.note", "Operator decision note"),
            Map.entry("PromoteCheckinClusterRequest.note", "Operator decision note on a promotion"),
            Map.entry("RejectContributionRequest.reason", "Operator decision note"),
            Map.entry("ResolveModerationFlagRequest.note", "Operator decision note"),
            Map.entry("GrantSubscriptionRequest.note", "Operator records why a plan was granted; never shown to a user"),
            Map.entry("GrantGuideRequest.note", "Operator records why a guide was vouched for; never shown to a user"),
            Map.entry("UpsertModerationTermRequest.note", "Operator note on a term list entry"),
            Map.entry("UpdateActivityOrderStatusRequest.reason", "Operator or partner order note"),
            Map.entry("UpdateHotelBookingStatusRequest.reason", "Operator or partner order note"),
            Map.entry("CreateLocationImageRequest.slogan", "Curated editorial copy written by the team"),
            Map.entry("UpdateLocationImageRequest.slogan", "Curated editorial copy written by the team"),
            Map.entry("CreateFoodRequest.description", "Curated catalogue copy written by the team"),
            Map.entry("UpdateFoodRequest.description", "Curated catalogue copy written by the team"),

            // Reporting and preview paths: filtering these would filter the complaint.
            Map.entry("ReportContentRequest.note", "The reporter describes the violation; filtering it would silence the report"),
            Map.entry("BlockUserRequest.reason", "The blocker's own note about why; filtering it would filter the complaint"),
            Map.entry("ModerationPreviewRequest.text", "This IS the filter being asked for its opinion"),

            // Bulk import from external sources. Blocking mid-import would abort a batch of
            // thousands; these paths are reviewed through the queue instead, and they are
            // reachable only with ROLE_ADMIN or ROLE_API_KEY.
            Map.entry("ImportPlaceRequest.title", "Bulk catalogue import, admin/API-key only; reviewed post-hoc"),
            Map.entry("ImportPlaceRequest.address", "Bulk catalogue import, admin/API-key only; reviewed post-hoc"),
            Map.entry("UpdatePlaceRequest.title", "Catalogue edit, admin/API-key only"),
            Map.entry("UpdatePlaceRequest.address", "Catalogue edit, admin/API-key only"),
            Map.entry("PlaceTranslationRequest.name", "Machine translation of already-filtered catalogue text"),
            Map.entry("PlaceTranslationRequest.description", "Machine translation of already-filtered catalogue text"),
            Map.entry("ImportActivityBookingRequest.title", "Bulk supplier import, admin/API-key only"),
            Map.entry("ImportActivityBookingRequest.text", "Bulk supplier import, admin/API-key only"),
            Map.entry("ImportActivityBookingRequest.content", "Bulk supplier import, admin/API-key only"),
            Map.entry("ImportActivityBookingRequest.description", "Bulk supplier import, admin/API-key only"),
            Map.entry("UpdateActivityBookingRequest.title", "Bulk supplier import, admin/API-key only"),
            Map.entry("UpdateActivityBookingRequest.text", "Bulk supplier import, admin/API-key only"),
            Map.entry("UpdateActivityBookingRequest.content", "Bulk supplier import, admin/API-key only"),
            Map.entry("UpdateActivityBookingRequest.description", "Bulk supplier import, admin/API-key only"),
            Map.entry("GorouteContributionReviewInput.text", "Internal import payload, not a user entry point"));

    @Test
    void everyFreeTextRequestFieldIsFilteredOrDeliberatelyExempt() {
        List<String> unguarded = new ArrayList<>();

        for (Class<?> type : requestClasses()) {
            for (Field field : allFields(type)) {
                if (!isCandidate(field)) {
                    continue;
                }
                String key = type.getSimpleName() + "." + field.getName();
                if (field.isAnnotationPresent(ModeratedText.class) || EXEMPT.containsKey(key)) {
                    continue;
                }
                unguarded.add(key);
            }
        }

        assertThat(unguarded)
                .as("Free-text request fields must be annotated with @ModeratedText, or added to "
                        + "ModeratedFieldCoverageTest.EXEMPT with the reason they are not user content")
                .isEmpty();
    }

    /** An exemption for a field that no longer exists hides the next real gap. */
    @Test
    void exemptionListHasNoStaleEntries() {
        Set<String> present = new LinkedHashSet<>();
        for (Class<?> type : requestClasses()) {
            for (Field field : allFields(type)) {
                present.add(type.getSimpleName() + "." + field.getName());
            }
        }

        assertThat(EXEMPT.keySet()).allSatisfy(key ->
                assertThat(present).as("Stale exemption: %s", key).contains(key));
    }

    private boolean isCandidate(Field field) {
        return field.getType() == String.class
                && !Modifier.isStatic(field.getModifiers())
                && !field.isSynthetic()
                && FREE_TEXT_NAMES.contains(field.getName().toLowerCase(Locale.ROOT));
    }

    private List<Field> allFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class;
             current = current.getSuperclass()) {
            fields.addAll(List.of(current.getDeclaredFields()));
        }
        // Nested payload classes carry their own text fields, and the runtime scanner
        // walks into them, so they are checked here under the outer class name.
        for (Class<?> nested : type.getDeclaredClasses()) {
            if (!isGeneratedBuilder(nested)) {
                fields.addAll(List.of(nested.getDeclaredFields()));
            }
        }
        return fields;
    }

    private List<Class<?>> requestClasses() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*")));

        List<Class<?>> classes = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(REQUEST_PACKAGE)) {
            try {
                Class<?> type = Class.forName(definition.getBeanClassName());
                // Nested types are reached through their outer class; Lombok builders are
                // generated mirrors of fields that are already checked on the DTO itself.
                if (type.getEnclosingClass() == null) {
                    classes.add(type);
                }
            } catch (ClassNotFoundException exception) {
                throw new IllegalStateException("Could not load " + definition.getBeanClassName(), exception);
            }
        }
        assertThat(classes).as("The request package should not be empty").isNotEmpty();
        return classes;
    }

    private boolean isGeneratedBuilder(Class<?> nested) {
        return nested.getSimpleName().endsWith("Builder");
    }
}
