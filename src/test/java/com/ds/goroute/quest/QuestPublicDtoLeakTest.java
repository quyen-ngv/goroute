package com.ds.goroute.quest;

import com.ds.goroute.quest.dto.QuestPublicDetailResponse;
import com.ds.goroute.quest.dto.QuestPublicSummaryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Content-control rules 2 and 3 (§7.3), made structural: no public quest shape may carry an
 * answer, an unreached checkpoint's coordinates, a location key, or unbought hint text.
 *
 * <p>A structural check rather than an endpoint check because the shape is the invariant: if the
 * field cannot exist on the DTO, no serializer, projection or future endpoint can leak it. The
 * run-scoped shapes that legitimately serve one checkpoint's coordinates are not public and are
 * not listed here.
 *
 * <p>One deliberate exception: a creator may choose to show the whole route (the default), and then
 * the detail page's {@code route} places each checkpoint — a PIN at its spot, an AREA only at its
 * offset search circle. Only {@code latitude}/{@code longitude} are allowed, and only there; every
 * other fragment stays forbidden in the route too.
 */
@DisplayName("Public quest DTOs never carry answers, coordinates, location keys or hints")
class QuestPublicDtoLeakTest {

    /** Field-name fragments that must never appear anywhere in a public quest DTO graph. */
    private static final List<String> FORBIDDEN = List.of(
            "answer", "latitude", "longitude", "coordinate", "geometry", "locationkey",
            "hint", "correct", "tolerance", "capture");

    /** The opt-in route (see the class comment): the only place a public shape may carry a spot. */
    private static final String OPT_IN_ROUTE = "QuestPublicDetailResponse.route.";
    private static final Set<String> OPT_IN_ROUTE_FIELDS = Set.of("latitude", "longitude");

    private static final List<Class<?>> PUBLIC_DTOS = List.of(
            QuestPublicSummaryResponse.class, QuestPublicDetailResponse.class);

    @Test
    void noForbiddenFieldInAnyPublicShape() {
        List<String> leaks = new ArrayList<>();
        for (Class<?> dto : PUBLIC_DTOS) {
            scan(dto, dto.getSimpleName(), leaks, new java.util.HashSet<>());
        }
        assertThat(leaks)
                .as("public quest DTO fields that could leak answers/coordinates/hints")
                .isEmpty();
    }

    @Test
    void theOptInRouteIsTheOnlyPlaceASpotMayAppear() {
        List<String> leaks = new ArrayList<>();
        scan(QuestPublicSummaryResponse.class, "QuestPublicSummaryResponse", leaks, new java.util.HashSet<>());
        assertThat(leaks).isEmpty();
        // The route itself is scanned (a list's element type), so a hint or an answer added there
        // would still fail.
        List<String> fields = new ArrayList<>();
        for (Field f : QuestPublicDetailResponse.RouteStop.class.getDeclaredFields()) {
            fields.add(f.getName());
        }
        assertThat(fields).contains("latitude", "longitude")
                .noneMatch(n -> n.toLowerCase(Locale.ROOT).contains("hint")
                        || n.toLowerCase(Locale.ROOT).contains("answer"));
    }

    private static void scan(Class<?> type, String path, List<String> leaks, Set<Class<?>> seen) {
        if (type == null || !type.getName().startsWith("com.ds.goroute.") || !seen.add(type)) {
            return;
        }
        for (Field field : type.getDeclaredFields()) {
            if (field.isSynthetic()) {
                continue;
            }
            String lower = field.getName().toLowerCase(Locale.ROOT);
            String fieldPath = path + "." + field.getName();
            boolean optIn = fieldPath.startsWith(OPT_IN_ROUTE) && OPT_IN_ROUTE_FIELDS.contains(field.getName());
            for (String bad : FORBIDDEN) {
                if (lower.contains(bad) && !optIn) {
                    leaks.add(fieldPath);
                }
            }
            // Recurse into nested project types (e.g. a checkpoint view), and into the element type
            // of a list of them, skipping JDK types.
            scan(field.getType(), fieldPath, leaks, seen);
            if (field.getGenericType() instanceof ParameterizedType generic) {
                for (Type arg : generic.getActualTypeArguments()) {
                    if (arg instanceof Class<?> element) {
                        scan(element, fieldPath, leaks, seen);
                    }
                }
            }
        }
    }
}
