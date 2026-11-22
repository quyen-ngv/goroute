package com.ds.goroute.dto.response;

import com.ds.goroute.type.ContentVisibility;
import com.ds.goroute.type.PostStatus;
import com.ds.goroute.type.PostType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * {@code PostResponse} of the contract (.docs/BLOG_POSTS_PLAN_2026-11-10.md §4). In a list
 * {@code body} is null; the detail, and every write that answers with the post, carry it.
 */
@Data
@Builder
public class PostResponse {
    private UUID id;
    private PostType postType;
    private String title;
    private String excerpt;
    private String body;
    private PostStatus status;
    private ContentVisibility visibility;
    private int readMinutes;
    private PostMediaResponse cover;
    /** Every live media of the post, cover and body, by position. */
    private List<PostMediaResponse> media;
    private List<PostPlaceResponse> places;
    private PostTripResponse trip;
    /**
     * Detail of a trip story only (null in lists and for articles): the stops of its trip. The
     * author gets every live stop, so the editor can tell which ones the story does not mention
     * yet; a reader gets the stops the body points at.
     */
    private List<PostStopResponse> stops;
    private PostAuthorResponse author;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean edited;
    private int likeCount;
    private boolean hasLiked;
    private boolean hasSaved;
    /** Active comments; 0 when the thread cannot be opened (not published + public, or taken down). */
    private int commentCount;
    /**
     * Boolean, not boolean: with a primitive Lombok writes {@code isOwn()} and Jackson would call
     * the property "own". The contract says "isOwn".
     */
    private Boolean isOwn;
    private boolean canEdit;
    private int version;

    /** Console only: an active content_takedowns row of type POST. Absent from app answers. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean hidden;
    /** Console only: the author is still users.managed_by_admin. Absent from app answers. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean isManagedAuthor;
}
