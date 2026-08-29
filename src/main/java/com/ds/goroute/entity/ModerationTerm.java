package com.ds.goroute.entity;

import com.ds.goroute.type.ModerationAction;
import com.ds.goroute.type.ModerationCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One entry of the administrable term list (MOD-02). An exemption is the same row with
 * {@code isExemption = true}; keeping both in one table lets the matcher load a single
 * snapshot and guarantees exemptions are evaluated against the same normalized form.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModerationTerm {
    private UUID id;
    private String term;
    private String normalizedTerm;
    private ModerationCategory category;
    private ModerationAction action;
    private String language;
    private Boolean isExemption;
    private String note;
    private Boolean isActive;
    private Long dataVersion;
    private UUID createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
