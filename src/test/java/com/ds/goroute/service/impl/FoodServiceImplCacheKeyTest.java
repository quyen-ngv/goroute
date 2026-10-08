package com.ds.goroute.service.impl;

import com.ds.goroute.config.filter.AcceptLanguageFilter;
import com.ds.goroute.dto.response.FoodSummaryResponse;
import com.ds.goroute.entity.FoodWithScoreRow;
import com.ds.goroute.repository.FoodRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.service.ExchangeRateService;
import com.ds.goroute.service.FoodService;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.service.PlaceSearchIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * The food list resolves names in the request language, so one cached entry per city/page must not be
 * served to a reader of another language.
 */
@SpringJUnitConfig(FoodServiceImplCacheKeyTest.Config.class)
class FoodServiceImplCacheKeyTest {

    @Configuration
    @EnableCaching
    static class Config {
        @Bean
        FoodRepository foodRepository() {
            return mock(FoodRepository.class);
        }

        @Bean("foodsByCityCacheManager")
        CacheManager foodsByCityCacheManager() {
            return new CaffeineCacheManager("foodsByCity");
        }

        @Bean
        FoodService foodService(FoodRepository foodRepository) {
            return new FoodServiceImpl(foodRepository, mock(PlaceRepository.class), mock(ExchangeRateService.class),
                    mock(ImageStorageCleanupService.class), mock(PlaceSearchIndexService.class));
        }
    }

    @Autowired
    private FoodService foodService;

    @Autowired
    private FoodRepository foodRepository;

    @Autowired
    private CacheManager foodsByCityCacheManager;

    @BeforeEach
    void setUp() {
        reset(foodRepository);
        foodsByCityCacheManager.getCache("foodsByCity").clear();
        FoodWithScoreRow row = FoodWithScoreRow.builder()
                .id(UUID.randomUUID())
                .nameVi("Phở bò")
                .nameEn("Beef pho")
                .score(90)
                .build();
        when(foodRepository.findFoodsByCity(anyString(), anyInt(), anyInt(), anyInt())).thenReturn(List.of(row));
    }

    @Test
    void cachesOneEntryPerRequestLanguage() throws Exception {
        String vietnamese = listInLanguage("vi");
        String english = listInLanguage("en");

        assertThat(vietnamese).isEqualTo("Phở bò");
        assertThat(english).isEqualTo("Beef pho");
        verify(foodRepository, times(2)).findFoodsByCity(anyString(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void reusesTheEntryForTheSameLanguage() throws Exception {
        listInLanguage("vi");
        String again = listInLanguage("vi");

        assertThat(again).isEqualTo("Phở bò");
        verify(foodRepository, times(1)).findFoodsByCity(anyString(), anyInt(), anyInt(), anyInt());
    }

    private String listInLanguage(String language) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AcceptLanguageFilter.HEADER_NAME, language);
        AtomicReference<List<FoodSummaryResponse>> result = new AtomicReference<>();
        new AcceptLanguageFilter().doFilter(request, new MockHttpServletResponse(),
                (req, res) -> result.set(foodService.listByCity("hanoi", 20, 0)));
        return result.get().get(0).getName();
    }
}
