package com.ds.goroute.service.assistant;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.AssistantProfileResponse;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.AssistantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * What Pengoo keeps about a person in every 1:1 thread (home city, group, diet, likes, pace,
 * budget): short facts, one per line in {@code assistant_profiles}. The worker rewrites the whole
 * list when a 1:1 answer learns something durable; the person sees it and edits it here.
 */
@Service
@RequiredArgsConstructor
public class AssistantProfileService {

    static final int MAX_ITEMS = 12;
    static final int MAX_ITEM_CHARS = 80;
    private static final Pattern LINE_BREAK = Pattern.compile("\\r?\\n|\\r");
    /** A list marker the model may put in front of a line ("- ", "• ", "* "). */
    private static final Pattern BULLET = Pattern.compile("^[-•*]\\s+");

    private final AssistantRepository repository;

    @Transactional(readOnly = true)
    public AssistantProfileResponse get(UUID userId) {
        return new AssistantProfileResponse(lines(repository.findProfile(userId).orElse(null)));
    }

    /** Replaces the list as the person wrote it; an empty list forgets everything. */
    @Transactional
    public AssistantProfileResponse replace(UUID userId, List<String> items) {
        List<String> kept = new ArrayList<>();
        for (String item : items == null ? List.<String>of() : items) {
            String value = item == null ? "" : item.trim();
            if (value.isEmpty() || value.length() > MAX_ITEM_CHARS) {
                throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                        "Each item must be 1 to " + MAX_ITEM_CHARS + " characters");
            }
            kept.add(value);
        }
        if (kept.size() > MAX_ITEMS) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "At most " + MAX_ITEMS + " items");
        }
        if (kept.isEmpty()) {
            repository.deleteProfile(userId);
        } else {
            repository.saveProfile(userId, String.join("\n", kept), null, LocalDateTime.now());
        }
        return new AssistantProfileResponse(kept);
    }

    /**
     * The model's text as profile lines: one fact per line, trimmed, without list markers, empty
     * lines dropped, each cut to {@link #MAX_ITEM_CHARS}, at most {@link #MAX_ITEMS}.
     */
    static List<String> lines(String text) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) return lines;
        for (String raw : LINE_BREAK.split(text)) {
            String line = BULLET.matcher(raw.trim()).replaceFirst("").trim();
            if (line.isEmpty()) continue;
            if (line.length() > MAX_ITEM_CHARS) line = line.substring(0, MAX_ITEM_CHARS).trim();
            if (!lines.contains(line)) lines.add(line);
            if (lines.size() >= MAX_ITEMS) break;
        }
        return lines;
    }
}
