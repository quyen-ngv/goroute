package com.ds.goroute.config.filter;

import com.ds.goroute.constant.RequestKeyConstant;
import com.ds.goroute.service.time.UserTimeZones;
import com.ds.goroute.utils.AppZones;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Reads the device's IANA zone from {@code X-Timezone} (e.g. {@code Asia/Tokyo}) for the current
 * request and remembers it for the signed-in user. Runs after the security chain, so the user id
 * attribute is already set.
 */
@Component
@Order(3)
public class ClientTimeZoneFilter implements Filter {

    public static final String HEADER_NAME = "X-Timezone";

    private static final ThreadLocal<ZoneId> ZONE_HOLDER = new ThreadLocal<>();

    private final UserTimeZones userTimeZones;

    public ClientTimeZoneFilter(@Lazy UserTimeZones userTimeZones) {
        this.userTimeZones = userTimeZones;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            if (request instanceof HttpServletRequest httpReq) {
                ZoneId zone = AppZones.parseOrNull(httpReq.getHeader(HEADER_NAME));
                if (zone != null) {
                    ZONE_HOLDER.set(zone);
                    if (httpReq.getAttribute(RequestKeyConstant.USER_ID) instanceof UUID userId) {
                        userTimeZones.remember(userId, zone);
                    }
                }
            }
            chain.doFilter(request, response);
        } finally {
            ZONE_HOLDER.remove();
        }
    }

    /** The zone the current request's device reported, or null. */
    public static ZoneId currentZone() {
        return ZONE_HOLDER.get();
    }
}
