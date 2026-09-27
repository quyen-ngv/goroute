package com.ds.goroute.quest.dto;

import java.util.List;

/**
 * An uploaded AR library file (§3.15). For a GLB, what the server read from it; {@code errors} is
 * empty when the file was accepted (a file with errors is not stored and has no URL).
 */
public record QuestArObjectFileResponse(
        String kind,
        String url,
        long bytes,
        Integer triangles,
        Integer joints,
        List<QuestArObjectView.Clip> clips,
        Boolean canWander,
        List<String> errors,
        List<String> warnings) {
}
