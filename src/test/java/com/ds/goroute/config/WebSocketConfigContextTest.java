package com.ds.goroute.config;

import com.ds.goroute.service.MarketplaceConversationAccessService;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.messaging.simp.user.SimpUserRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class WebSocketConfigContextTest {

    @Test
    void brokerAndConfigurerStartWithoutARegistryDependencyCycle() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(MarketplaceConversationAccessService.class,
                    () -> mock(MarketplaceConversationAccessService.class));
            context.registerBean(TripAccessGuard.class, () -> mock(TripAccessGuard.class));
            context.registerBean(JwtUtils.class, () -> mock(JwtUtils.class));
            context.register(WebSocketConfig.class);

            context.refresh();

            assertThat(context.getBean(WebSocketConfig.class)).isNotNull();
            assertThat(context.getBean(SimpUserRegistry.class)).isNotNull();
        }
    }
}
