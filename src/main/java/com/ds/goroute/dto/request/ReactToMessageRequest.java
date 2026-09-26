package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * One tap of one reaction.
 *
 * <p>The emoji comes from a fixed list rather than from the keyboard. A free-text column
 * that is displayed to everyone in a thread is user-generated content, and this one would
 * have gone in without passing the filter; restricting the alphabet removes the question
 * instead of answering it.
 */
@Data
public class ReactToMessageRequest {

    /** The reactions a client may send. Anything else is refused. */
    public static final List<String> ALLOWED = List.of("❤️", "👍", "👎", "😂", "😮", "😢", "🙏", "🔥");

    @NotBlank
    private String emoji;

    public boolean isAllowed() {
        return emoji != null && ALLOWED.contains(emoji.trim());
    }
}
