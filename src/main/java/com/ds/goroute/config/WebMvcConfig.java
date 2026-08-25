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
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties({
        ApiRequestLimitsProperties.class,
        ScrapeHttpClientProperties.class,
        AiTripWorkerProperties.class,
        FileUploadProperties.class,
        ImgpressProperties.class
})
public class WebMvcConfig implements WebMvcConfigurer {

    private final ApiRequestLimitsInterceptor apiRequestLimitsInterceptor;
    private final ScrapeHttpClientProperties scrapeHttpClientProperties;
    private final AiTripWorkerProperties aiTripWorkerProperties;

    public WebMvcConfig(
            ApiRequestLimitsInterceptor apiRequestLimitsInterceptor,
            ScrapeHttpClientProperties scrapeHttpClientProperties,
            AiTripWorkerProperties aiTripWorkerProperties) {
        this.apiRequestLimitsInterceptor = apiRequestLimitsInterceptor;
        this.scrapeHttpClientProperties = scrapeHttpClientProperties;
        this.aiTripWorkerProperties = aiTripWorkerProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiRequestLimitsInterceptor).addPathPatterns("/v1/api/**");
    }

    @Bean
    @Primary
    public RestTemplate restTemplate() {
        return new RestTemplate();
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
