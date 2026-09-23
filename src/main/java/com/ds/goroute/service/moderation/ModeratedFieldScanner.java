package com.ds.goroute.service.moderation;

import com.ds.goroute.annotations.ModeratedText;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Walks a request object and hands every {@link ModeratedText} field to a visitor.
 *
 * <p>Nested request objects are traversed automatically, so a list of nested items only
 * needs the annotation on its leaf text field. Field lookup is resolved once per class
 * and cached; the per-request cost is then a handful of reflective reads.
 */
public final class ModeratedFieldScanner {

    /** Only our own request objects are traversed; never framework or JDK types. */
    private static final String REQUEST_PACKAGE_PREFIX = "com.ds.goroute.";

    private static final int MAX_DEPTH = 6;

    private static final Map<Class<?>, List<Field>> RELEVANT_FIELDS = new ConcurrentHashMap<>();

    /** Receives one annotated text value. */
    @FunctionalInterface
    public interface TextVisitor {
        void visit(ModeratedText annotation, String fieldLabel, String value);
    }

    private ModeratedFieldScanner() {
    }

    public static void walk(Object root, TextVisitor visitor) {
        if (root != null) {
            walk(root, visitor, 0, java.util.Collections.newSetFromMap(new IdentityHashMap<>()));
        }
    }

    private static void walk(Object node, TextVisitor visitor, int depth, Set<Object> seen) {
        if (node == null || depth > MAX_DEPTH || !seen.add(node)) {
            return;
        }
        for (Field field : relevantFields(node.getClass())) {
            Object value = read(field, node);
            if (value == null) {
                continue;
            }
            ModeratedText annotation = field.getAnnotation(ModeratedText.class);
            String label = annotation == null || annotation.label().isBlank()
                    ? field.getName()
                    : annotation.label();
            if (value instanceof String text) {
                if (annotation != null) {
                    visitor.visit(annotation, label, text);
                }
                continue;
            }
            if (value instanceof Collection<?> || value instanceof Map<?, ?>) {
                visitContainer(annotation, label, value, visitor, depth, seen);
                continue;
            }
            if (isOwnType(value.getClass())) {
                walk(value, visitor, depth + 1, seen);
            }
        }
    }

    /**
     * Lists and maps of free-form JSON, to whatever depth they nest.
     *
     * <p>A map matters because some requests are deliberately schemaless — the onboarding
     * wizard stores a step's answers as a map so a new screen does not need a new DTO. Text
     * typed by a partner is still text typed by a partner, so it goes through the same
     * filter; without this the annotation on such a field would be quietly decorative.
     */
    private static void visitContainer(ModeratedText annotation, String label, Object container,
                                       TextVisitor visitor, int depth, Set<Object> seen) {
        if (depth > MAX_DEPTH) {
            return;
        }
        Collection<?> values = container instanceof Map<?, ?> map
                ? map.values()
                : (Collection<?>) container;
        for (Object element : values) {
            if (element == null) {
                continue;
            }
            if (element instanceof String text) {
                if (annotation != null) {
                    visitor.visit(annotation, label, text);
                }
            } else if (element instanceof Collection<?> || element instanceof Map<?, ?>) {
                visitContainer(annotation, label, element, visitor, depth + 1, seen);
            } else if (isOwnType(element.getClass())) {
                walk(element, visitor, depth + 1, seen);
            }
        }
    }

    /**
     * Fields worth reading at runtime: annotated text, or a nested object of our own that
     * might contain annotated text. Everything else is skipped so the walk stays cheap.
     */
    private static List<Field> relevantFields(Class<?> type) {
        return RELEVANT_FIELDS.computeIfAbsent(type, ModeratedFieldScanner::scan);
    }

    private static List<Field> scan(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = type; current != null && isOwnType(current); current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                if (field.isAnnotationPresent(ModeratedText.class)
                        || Collection.class.isAssignableFrom(field.getType())
                        || Map.class.isAssignableFrom(field.getType())
                        || isOwnType(field.getType())) {
                    field.setAccessible(true);
                    fields.add(field);
                }
            }
        }
        return List.copyOf(fields);
    }

    private static Object read(Field field, Object target) {
        try {
            return field.get(target);
        } catch (IllegalAccessException exception) {
            return null;
        }
    }

    private static boolean isOwnType(Class<?> type) {
        return type != null
                && !type.isPrimitive()
                && type.getName().startsWith(REQUEST_PACKAGE_PREFIX);
    }
}
