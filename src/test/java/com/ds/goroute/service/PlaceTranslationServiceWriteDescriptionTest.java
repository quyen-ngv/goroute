package com.ds.goroute.service;

import com.ds.goroute.entity.PlaceTranslation;
import com.ds.goroute.mapper.PlaceTranslationMapper;
import com.ds.goroute.type.TranslationLocale;
import com.ds.goroute.type.TranslationSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceTranslationServiceWriteDescriptionTest {

    private final PlaceTranslationMapper mapper = mock(PlaceTranslationMapper.class);
    private final PlaceTranslationService service = new PlaceTranslationService(mapper);

    @Test
    void writesTheDescriptionAsManualWithTheShownNameForANewRow() {
        UUID placeId = UUID.randomUUID();
        when(mapper.upsertDescription(any())).thenReturn(1);

        boolean written = service.writeDescription(placeId, TranslationLocale.EN,
                "  A century-old beef pho shop.  ", " Pho Thin ", TranslationSource.MANUAL);

        ArgumentCaptor<PlaceTranslation> captor = ArgumentCaptor.forClass(PlaceTranslation.class);
        verify(mapper).upsertDescription(captor.capture());
        assertThat(written).isTrue();
        assertThat(captor.getValue().getPlaceId()).isEqualTo(placeId);
        assertThat(captor.getValue().getLocale()).isEqualTo(TranslationLocale.EN);
        assertThat(captor.getValue().getDescription()).isEqualTo("A century-old beef pho shop.");
        assertThat(captor.getValue().getName()).isEqualTo("Pho Thin");
        assertThat(captor.getValue().getTranslationSource()).isEqualTo(TranslationSource.MANUAL);
    }

    @Test
    void reportsWhenAHigherRankedRowKeptItsText() {
        when(mapper.upsertDescription(any())).thenReturn(0);

        assertThat(service.writeDescription(UUID.randomUUID(), TranslationLocale.VI,
                "Mô tả từ Google cho quán.", "Phở", TranslationSource.GOOGLE)).isFalse();
    }
}
