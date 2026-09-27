package com.ds.goroute.quest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A run played on the phone, uploaded once it is finished (local-first play). The server replays
 * it: each arrival position is checked against the checkpoint, each answer re-graded, each clue
 * charged. What the phone decided is never taken as proof.
 *
 * @param clientRunId the phone's id for the run; a second upload with the same id changes nothing.
 * @param versionId   the version the pack was downloaded at.
 */
public record QuestLocalRunRequest(
        @NotBlank @Size(max = 64) String clientRunId,
        @NotNull UUID versionId,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        @Valid @Size(max = 200) List<Arrival> arrivals,
        @Valid @Size(max = 2000) List<Answer> answers,
        @Valid @Size(max = 500) List<Clue> clues,
        @Valid @Size(max = 2000) List<StopVisit> stopVisits,
        @Valid @Size(max = 200) List<ArTap> arTaps) {

    public record Arrival(@NotNull UUID checkpointId, BigDecimal latitude, BigDecimal longitude,
                          BigDecimal accuracyMeters, LocalDateTime arrivedAt) {
    }

    /** The answer that was accepted on the phone (or the last one tried), and how many tries it took. */
    public record Answer(@NotNull UUID questionId, @Size(max = 2000) String text, List<UUID> choiceIds,
                         Integer guessCount) {
    }

    public record Clue(@NotNull UUID checkpointId, int tier, LocalDateTime boughtAt) {
    }

    /** An AR object tapped (§3.15): where the phone was, and how the object was anchored. */
    public record ArTap(@NotNull UUID checkpointId, BigDecimal latitude, BigDecimal longitude,
                        BigDecimal accuracyMeters, @Size(max = 10) String anchorMode, LocalDateTime tappedAt) {
    }

    public record StopVisit(@NotNull UUID stopId, BigDecimal latitude, BigDecimal longitude,
                            BigDecimal accuracyMeters, LocalDateTime visitedAt) {
    }
}
