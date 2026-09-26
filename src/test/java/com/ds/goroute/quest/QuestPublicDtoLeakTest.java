package com.ds.goroute.quest;

import com.ds.goroute.quest.dto.QuestPublicDetailResponse;
import com.ds.goroute.quest.dto.QuestPublicSummaryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
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
 */
@DisplayName("Public quest DTOs never carry answers, coordinates, location keys or hints")
class QuestPublicDtoLeakTest {

    /** Field-name fragments that must never appear anywhere in a public quest DTO graph. */
    private static final List<String> FORBIDDEN = List.of(
            "answer", "latitude", "longitude", "coordinate", "geometry", "locationkey",
            "hint", "correct", "tolerance", "capture");

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

    private static void scan(Class<?> type, String path, List<String> leaks, Set<Class<?>> seen) {
        if (type == null || type.getName().startsWith("java.") || !seen.add(type)) {
            return;
        }
        for (Field field : type.getDeclaredFields()) {
            if (field.isSynthetic()) {
                continue;
            }
            String lower = field.getName().toLowerCase(Locale.ROOT);
            for (String bad : FORBIDDEN) {
                if (lower.contains(bad)) {
                    leaks.add(path + "." + field.getName());
                }
            }
            // Recurse into nested project types (e.g. a checkpoint view), skipping JDK types.
            Class<?> fieldType = field.getType();
            if (fieldType.getName().startsWith("com.ds.goroute.")) {
                scan(fieldType, path + "." + field.getName(), leaks, seen);
            }
        }
    }
}
