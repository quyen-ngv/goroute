package com.ds.goroute.meetup;

import com.ds.goroute.type.MeetupEffectiveStatus;
import com.ds.goroute.type.MeetupStatus;
import com.ds.goroute.type.MeetupStatus.Actor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MeetupStatus transition table")
class MeetupStatusTest {

    @Test
    void everyMeetupStartsInReview() {
        assertThat(MeetupStatus.initial()).isEqualTo(MeetupStatus.PENDING_REVIEW);
    }

    @Test
    void onlyAnAdminDecidesAReview() {
        assertThat(MeetupStatus.PENDING_REVIEW.canTransitionTo(MeetupStatus.APPROVED, Actor.ADMIN)).isTrue();
        assertThat(MeetupStatus.PENDING_REVIEW.canTransitionTo(MeetupStatus.REJECTED, Actor.ADMIN)).isTrue();
        assertThat(MeetupStatus.PENDING_REVIEW.canTransitionTo(MeetupStatus.APPROVED, Actor.HOST)).isFalse();
        assertThat(MeetupStatus.PENDING_REVIEW.canTransitionTo(MeetupStatus.APPROVED, Actor.SYSTEM)).isFalse();
    }

    @Test
    void theHostEditsWithdrawsResubmitsAndCancels() {
        assertThat(MeetupStatus.PENDING_REVIEW.canTransitionTo(MeetupStatus.PENDING_REVIEW, Actor.HOST)).isTrue();
        assertThat(MeetupStatus.PENDING_REVIEW.canTransitionTo(MeetupStatus.WITHDRAWN, Actor.HOST)).isTrue();
        assertThat(MeetupStatus.REJECTED.canTransitionTo(MeetupStatus.PENDING_REVIEW, Actor.HOST)).isTrue();
        assertThat(MeetupStatus.APPROVED.canTransitionTo(MeetupStatus.CANCELLED, Actor.HOST)).isTrue();
        assertThat(MeetupStatus.APPROVED.canTransitionTo(MeetupStatus.REMOVED, Actor.HOST)).isFalse();
        assertThat(MeetupStatus.APPROVED.canTransitionTo(MeetupStatus.REMOVED, Actor.ADMIN)).isTrue();
    }

    @Test
    void nothingIsEditedAfterApprovalAndClosedStatesAreFinal() {
        assertThat(MeetupStatus.APPROVED.isHostEditable()).isFalse();
        assertThat(MeetupStatus.PENDING_REVIEW.isHostEditable()).isTrue();
        assertThat(MeetupStatus.REJECTED.isHostEditable()).isTrue();
        for (MeetupStatus closed : new MeetupStatus[] {
                MeetupStatus.WITHDRAWN, MeetupStatus.CANCELLED, MeetupStatus.REMOVED}) {
            for (MeetupStatus target : MeetupStatus.values()) {
                for (Actor actor : Actor.values()) {
                    assertThat(closed.canTransitionTo(target, actor)).as("%s → %s by %s", closed, target, actor)
                            .isFalse();
                }
            }
        }
    }

    @Test
    void pastIsComputedNotStored() {
        assertThat(MeetupEffectiveStatus.of(MeetupStatus.PENDING_REVIEW, true)).isEqualTo(MeetupEffectiveStatus.EXPIRED);
        assertThat(MeetupEffectiveStatus.of(MeetupStatus.APPROVED, true)).isEqualTo(MeetupEffectiveStatus.ENDED);
        assertThat(MeetupEffectiveStatus.of(MeetupStatus.APPROVED, false)).isEqualTo(MeetupEffectiveStatus.APPROVED);
        assertThat(MeetupEffectiveStatus.of(MeetupStatus.CANCELLED, true)).isEqualTo(MeetupEffectiveStatus.CANCELLED);
    }
}
