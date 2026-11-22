package com.ds.goroute.service.post;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The markdown rules of a post body (.docs/BLOG_POSTS_PLAN_2026-11-10.md §3).
 *
 * <p>Images and videos appear only as {@code ![caption](media:<uuid>)}, pointing at the post's
 * own media rows. Anything else that would draw a picture -- an outside URL, a reference-style
 * image, an HTML {@code <img>} -- is refused on save rather than left to every renderer to ignore:
 * an outside image would let a post load files the product never moderated.
 */
public final class PostMarkdown {

    /** Excerpt length before the ellipsis, cut at a word boundary. */
    public static final int EXCERPT_LENGTH = 240;

    /** Reading speed behind read_minutes. */
    public static final int WORDS_PER_MINUTE = 200;

    private static final String MEDIA_SCHEME = "media:";

    /** {@code ![alt](target "title")}; the target may sit in angle brackets. */
    private static final Pattern INLINE_IMAGE = Pattern.compile(
            "!\\[[^\\]]*]\\(\\s*<?([^\\s)>]*)>?(?:\\s+(?:\"[^\"]*\"|'[^']*'))?\\s*\\)");

    /** {@code ![alt][ref]} or {@code ![alt][]}: the target lives in a definition elsewhere. */
    private static final Pattern REFERENCE_IMAGE = Pattern.compile("!\\[[^\\]]*]\\[[^\\]]*]");

    private static final Pattern HTML_IMAGE = Pattern.compile("<\\s*(img|video|iframe|picture|source)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern MEDIA_ID = Pattern.compile(
            "media:([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})");

    /**
     * A heading that points at a stop of the trip ({@code ### [name](activity:<uuid>)}), a catalogue
     * place ({@code ### [name](place:<uuid>)}) or a day ({@code ## [label](day:N)}). The label is
     * the fallback the heading keeps showing when the target is gone or the reader is an old client.
     */
    private static final Pattern REF_LINK = Pattern.compile(
            "]\\((activity|place):([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})\\)");

    private static final Pattern LINK = Pattern.compile("\\[([^\\]]*)]\\([^)]*\\)");
    private static final Pattern HEADING = Pattern.compile("(?m)^\\s{0,3}#{1,6}\\s*");
    private static final Pattern QUOTE = Pattern.compile("(?m)^\\s{0,3}>\\s?");
    private static final Pattern LIST_MARKER = Pattern.compile("(?m)^\\s*(?:[-*+]|\\d+[.)])\\s+");
    private static final Pattern RULE = Pattern.compile("(?m)^\\s{0,3}(?:[-*_]\\s*){3,}$");
    private static final Pattern EMPHASIS = Pattern.compile("(\\*{1,3}|_{1,3}|~~|`+)");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private PostMarkdown() {
    }

    /**
     * Whether every picture in the body is {@code media:<id>} of one of {@code allowedMediaIds}.
     */
    public static boolean hasOnlyOwnMedia(String body, Set<UUID> allowedMediaIds) {
        if (body == null || body.isEmpty()) {
            return true;
        }
        if (REFERENCE_IMAGE.matcher(body).find() || HTML_IMAGE.matcher(body).find()) {
            return false;
        }
        Matcher image = INLINE_IMAGE.matcher(body);
        while (image.find()) {
            String target = image.group(1);
            if (!target.toLowerCase(Locale.ROOT).startsWith(MEDIA_SCHEME)) {
                return false;
            }
            UUID mediaId = parseUuid(target.substring(MEDIA_SCHEME.length()));
            if (mediaId == null || !allowedMediaIds.contains(mediaId)) {
                return false;
            }
        }
        // "![" that is not a well-formed inline image is a picture the pattern could not read.
        // Counting both sides catches it without a full CommonMark parser.
        return countOccurrences(body, "![") == countInlineImages(body);
    }

    /** Every {@code media:<uuid>} the body names, pictures or not, so a still-used media is never deleted. */
    public static Set<UUID> referencedMediaIds(String body) {
        Set<UUID> ids = new LinkedHashSet<>();
        if (body == null || body.isEmpty()) {
            return ids;
        }
        Matcher matcher = MEDIA_ID.matcher(body);
        while (matcher.find()) {
            UUID id = parseUuid(matcher.group(1));
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    /** The body as plain words: no markdown syntax, no pictures, whitespace collapsed. */
    public static String plainText(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String text = INLINE_IMAGE.matcher(body).replaceAll(" ");
        text = REFERENCE_IMAGE.matcher(text).replaceAll(" ");
        text = LINK.matcher(text).replaceAll("$1");
        text = RULE.matcher(text).replaceAll(" ");
        text = HEADING.matcher(text).replaceAll("");
        text = QUOTE.matcher(text).replaceAll("");
        text = LIST_MARKER.matcher(text).replaceAll("");
        text = HTML_TAG.matcher(text).replaceAll(" ");
        text = EMPHASIS.matcher(text).replaceAll("");
        return WHITESPACE.matcher(text).replaceAll(" ").trim();
    }

    /**
     * The automatic excerpt: the plain text cut to {@link #EXCERPT_LENGTH} characters at a word
     * boundary, with "…" when anything was cut. Null for an empty body.
     */
    public static String excerpt(String body) {
        String text = plainText(body);
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() <= EXCERPT_LENGTH) {
            return text;
        }
        int cut = text.lastIndexOf(' ', EXCERPT_LENGTH);
        // One word longer than the whole excerpt (a URL, say) is cut where it is.
        String head = cut <= 0 ? text.substring(0, EXCERPT_LENGTH) : text.substring(0, cut);
        return head.stripTrailing() + "…";
    }

    /** {@code max(1, ceil(words / 200))}. */
    public static int readMinutes(String body) {
        String text = plainText(body);
        int words = text.isEmpty() ? 0 : text.split(" ").length;
        return Math.max(1, (words + WORDS_PER_MINUTE - 1) / WORDS_PER_MINUTE);
    }

    /** The markdown line that shows one media. */
    public static String mediaLine(UUID mediaId) {
        return "![](" + MEDIA_SCHEME + mediaId + ")";
    }

    /**
     * Several media on consecutive lines, no blank line between: one gallery block (the app draws
     * them side by side; a plain markdown renderer shows them one under the other).
     */
    public static String galleryBlock(List<UUID> mediaIds) {
        return mediaIds.stream().map(PostMarkdown::mediaLine).collect(Collectors.joining("\n"));
    }

    /** {@code ## [label](day:N)}: a day of the trip story. */
    public static String dayHeading(String label, int dayNumber) {
        return "## [" + refLabel(label) + "](day:" + dayNumber + ")";
    }

    /** {@code ### [name](activity:<id>)}: a stop of the trip the story is about. */
    public static String stopHeading(String name, UUID activityId) {
        return "### [" + refLabel(name) + "](activity:" + activityId + ")";
    }

    /** Every {@code activity:<uuid>} the body points at, in order of appearance. */
    public static Set<UUID> referencedActivityIds(String body) {
        return referencedIds(body, "activity");
    }

    /** Every {@code place:<uuid>} the body points at, in order of appearance. */
    public static Set<UUID> referencedPlaceIds(String body) {
        return referencedIds(body, "place");
    }

    private static Set<UUID> referencedIds(String body, String scheme) {
        Set<UUID> ids = new LinkedHashSet<>();
        if (body == null || body.isEmpty()) {
            return ids;
        }
        Matcher matcher = REF_LINK.matcher(body);
        while (matcher.find()) {
            UUID id = scheme.equals(matcher.group(1)) ? parseUuid(matcher.group(2)) : null;
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    /** A link label cannot hold brackets or line breaks without ending the link early. */
    private static String refLabel(String label) {
        return label.replace('\n', ' ').replace('[', '(').replace(']', ')').trim();
    }

    private static int countInlineImages(String body) {
        int count = 0;
        Matcher matcher = INLINE_IMAGE.matcher(body);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int from = text.indexOf(needle);
        while (from >= 0) {
            count++;
            from = text.indexOf(needle, from + needle.length());
        }
        return count;
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
