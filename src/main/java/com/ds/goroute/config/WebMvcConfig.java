package com.ds.goroute.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableConfigurationProperties({
        ApiRequestLimitsProperties.class,
        ScrapeHttpClientProperties.class,
        AiTripWorkerProperties.class,
        FileUploadProperties.class,
        ImgpressProperties.class,
        ImageModerationProperties.class,
        OpenAiImageModerationProperties.class,
        PlaceImportWatchdogProperties.class
})
public class WebMvcConfig implements WebMvcConfigurer {

    private final CurrentUserArgumentResolver currentUserArgumentResolver;
    private final ApiRequestLimitsInterceptor apiRequestLimitsInterceptor;
    private final ScrapeHttpClientProperties scrapeHttpClientProperties;
    private final AiTripWorkerProperties aiTripWorkerProperties;

    public WebMvcConfig(
            CurrentUserArgumentResolver currentUserArgumentResolver,
            ApiRequestLimitsInterceptor apiRequestLimitsInterceptor,
            ScrapeHttpClientProperties scrapeHttpClientProperties,
            AiTripWorkerProperties aiTripWorkerProperties) {
        this.currentUserArgumentResolver = currentUserArgumentResolver;
        this.apiRequestLimitsInterceptor = apiRequestLimitsInterceptor;
        this.scrapeHttpClientProperties = scrapeHttpClientProperties;
        this.aiTripWorkerProperties = aiTripWorkerProperties;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiRequestLimitsInterceptor).addPathPatterns("/v1/api/**");
    }

    /**
     * The shared template: image migration downloads and Apple token verification ride on it.
     *
     * <p>It used to be a bare {@code new RestTemplate()}, which waits for ever. A hung upstream
     * therefore pinned a request thread until the socket died on its own, and every caller that
     * means to handle a failure only ever sees one when an exception is actually thrown.
     * Five seconds to connect is generous for any reachable host; thirty to read covers the
     * slowest thing on this template, which is pulling a full-size image body.
     */
    @Bean
    @Primary
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        return new RestTemplate(requestFactory);
    }

    @Bean("scrapeRestTemplate")
    public RestTemplate scrapeRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(scrapeHttpClientProperties.getConnectTimeout());
        requestFactory.setReadTimeout(scrapeHttpClientProperties.getReadTimeout());
        return new RestTemplate(requestFactory);
    }

    @Bean("aiTripWorkerRestTemplate")
    public RestTemplate aiTripWorkerRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(aiTripWorkerProperties.getWorker().getConnectTimeout());
        requestFactory.setReadTimeout(aiTripWorkerProperties.getWorker().getReadTimeout());
        return new RestTemplate(requestFactory);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Reverse-proxy prefix: /goroute/goroute-theme.css -> classpath:/static/goroute-theme.css
        registry.addResourceHandler("/goroute/**")
                .addResourceLocations("classpath:/static/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/share/{tripId}").setViewName("forward:/share-trip.html");
        registry.addViewController("/goroute/share/{tripId}").setViewName("forward:/share-trip.html");
    }
}
