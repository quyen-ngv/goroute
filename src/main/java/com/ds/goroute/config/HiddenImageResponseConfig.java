package com.ds.goroute.config;

import com.ds.goroute.service.HiddenImageService;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

/**
 * Swaps the URL of an image moderation hid for a placeholder in every JSON response.
 *
 * <p>Applied to a copy of the HTTP converter's mapper only: the shared {@code ObjectMapper}
 * also writes JSON into the database, and a hidden image an operator later restores must
 * still be there. Admin endpoints are left alone so the review queue shows the real image.
 */
@Configuration
public class HiddenImageResponseConfig implements WebMvcConfigurer {

    private static final String ADMIN_PATH_PREFIX = "/v1/api/admin";

    /** Resolved on first use: MVC configurers are built before most services exist. */
    private final ObjectProvider<HiddenImageService> hiddenImageServiceProvider;
    private volatile HiddenImageService hiddenImageService;

    public HiddenImageResponseConfig(ObjectProvider<HiddenImageService> hiddenImageServiceProvider) {
        this.hiddenImageServiceProvider = hiddenImageServiceProvider;
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        SimpleModule module = new SimpleModule("hidden-images");
        module.addSerializer(String.class, new HiddenImageUrlSerializer(this::hiddenImageService));
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter jackson) {
                jackson.setObjectMapper(jackson.getObjectMapper().copy().registerModule(module));
            }
        }
    }

    /** Looked up once; every string in every response goes through the serializer. */
    private HiddenImageService hiddenImageService() {
        HiddenImageService resolved = hiddenImageService;
        if (resolved == null) {
            resolved = hiddenImageServiceProvider.getObject();
            hiddenImageService = resolved;
        }
        return resolved;
    }

    static final class HiddenImageUrlSerializer extends StdSerializer<String> {

        private final transient Supplier<HiddenImageService> hiddenImageService;

        HiddenImageUrlSerializer(Supplier<HiddenImageService> hiddenImageService) {
            super(String.class);
            this.hiddenImageService = hiddenImageService;
        }

        @Override
        public void serialize(String value, JsonGenerator generator, SerializerProvider provider) throws IOException {
            HiddenImageService images = hiddenImageService.get();
            if (images.isHidden(value) && !isAdminRequest()) {
                generator.writeString(images.placeholderUrl());
                return;
            }
            generator.writeString(value);
        }

        private static boolean isAdminRequest() {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                HttpServletRequest request = attributes.getRequest();
                return request.getRequestURI().startsWith(ADMIN_PATH_PREFIX);
            }
            return false;
        }
    }
}
