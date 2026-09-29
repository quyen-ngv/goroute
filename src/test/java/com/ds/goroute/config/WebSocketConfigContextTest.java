package com.ds.goroute.config;

import com.ds.goroute.service.MarketplaceConversationAccessService;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.service.UserRealtimePublisher;
import com.ds.goroute.service.realtime.RealtimeSessionRegistry;
import com.ds.goroute.service.realtime.TripRealtimeAccessCache;
import com.ds.goroute.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.TaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class WebSocketConfigContextTest {

    @Test
    void brokerAndConfigurerStartWithoutADependencyCycle() {
        try (var context = context()) {
            assertThat(context.getBean(WebSocketConfig.class)).isNotNull();
            assertThat(context.getBean(SimpUserRegistry.class)).isNotNull();
            assertThat(context.getBean(UserRealtimePublisher.class)).isNotNull();
        }
    }

    /** Without a scheduler the broker answers CONNECT with heart-beat:0,0. */
    @Test
    void theBrokerHeartbeatsEveryTenSecondsOnItsOwnScheduler() {
        try (var context = context()) {
            SimpleBrokerMessageHandler broker = context.getBean(SimpleBrokerMessageHandler.class);

            assertThat(broker.getHeartbeatValue()).containsExactly(10_000L, 10_000L);
            assertThat(broker.getTaskScheduler()).isNotNull();
            // Reusing the broker's scheduler adds no TaskScheduler bean that @Scheduled could pick.
            assertThat(context.getBeanNamesForType(TaskScheduler.class))
                    .containsExactly("messageBrokerTaskScheduler");
        }
    }

    private AnnotationConfigApplicationContext context() {
        var context = new AnnotationConfigApplicationContext();
        context.registerBean(MarketplaceConversationAccessService.class,
                () -> mock(MarketplaceConversationAccessService.class));
        context.registerBean(TripAccessGuard.class, () -> mock(TripAccessGuard.class));
        context.registerBean(TripRealtimeAccessCache.class, () -> mock(TripRealtimeAccessCache.class));
        context.registerBean(RealtimeSessionRegistry.class);
        context.registerBean(JwtUtils.class, () -> mock(JwtUtils.class));
        context.registerBean(UserRealtimePublisher.class);
        context.register(WebSocketConfig.class);
        context.refresh();
        return context;
    }
}
