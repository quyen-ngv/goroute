package com.ds.goroute.meetup;

import com.ds.goroute.service.notification.NotificationTemplateRenderer;
import com.ds.goroute.type.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Meetup notification templates")
class MeetupNotificationTemplatesTest {

    private final NotificationTemplateRenderer renderer = new NotificationTemplateRenderer();

    @Test
    void theReasonIsOptionalAndTheSentenceStillEndsCleanly() {
        Map<String, Object> data = Map.of("meetupTitle", "chạy bộ quanh hồ Tây", "hostName", "Linh", "reason", "");

        assertThat(renderer.render(NotificationType.MEETUP_CANCELLED, data, "vi").body())
                .isEqualTo("“chạy bộ quanh hồ Tây” của Linh đã bị huỷ.");
        assertThat(renderer.render(NotificationType.MEETUP_REJECTED, data, "en").body())
                .isEqualTo("“chạy bộ quanh hồ Tây” was not approved.");
    }

    @Test
    void theRequesterAndTheHostAreNamed() {
        Map<String, Object> data = Map.of("meetupTitle", "uống cà phê", "hostName", "Linh", "userName", "Minh");

        assertThat(renderer.render(NotificationType.MEETUP_JOIN_REQUESTED, data, "vi").body())
                .isEqualTo("Minh muốn tham gia “uống cà phê”.");
        assertThat(renderer.render(NotificationType.MEETUP_REQUEST_ACCEPTED, data, "vi").body())
                .isEqualTo("Linh đã đồng ý cho bạn tham gia “uống cà phê”.");
    }
}
