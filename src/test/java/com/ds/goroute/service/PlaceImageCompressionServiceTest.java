package com.ds.goroute.service;

import com.ds.goroute.config.CaesiumProperties;
import com.ds.goroute.dto.request.CompressPlaceImagesRequest;
import com.ds.goroute.dto.response.PlaceImageCompressionResponse;
import com.ds.goroute.entity.PlaceImageState;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ImageCompressionRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.service.imagecompression.StoredImageCompressor;
import com.ds.goroute.thirdparty.caesium.CaesiumClient;
import com.ds.goroute.type.PlaceImageCompressionOutcome;
import com.ds.goroute.type.PlaceImageCompressionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceImageCompressionServiceTest {

    private static final String OURS = "https://onestudy.id.vn/resource/goroute/places/p1/a.webp";
    private static final String SMALL = "https://onestudy.id.vn/resource/goroute/places/p1/b.webp";
    private static final String GOOGLE = "https://lh3.googleusercontent.com/x=s0";

    private final PlaceRepository placeRepository = mock(PlaceRepository.class);
    private final StorageService storageService = mock(StorageService.class);
    private final CaesiumClient caesiumClient = mock(CaesiumClient.class);
    private final ImageCompressionRepository imageCompressionRepository = mock(ImageCompressionRepository.class);
    private final PlaceImageCompressionService service = new PlaceImageCompressionService(
            placeRepository, storageService, caesiumClient, new CaesiumProperties(),
            new ObjectMapper().registerModule(new JavaTimeModule()),
            new StoredImageCompressor(storageService, caesiumClient, imageCompressionRepository),
            imageCompressionRepository);
    private final UUID placeId = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    @Test
    void overwritesOwnFilesUnderTheSameKeyAndLeavesTheRestAlone() {
        place(OURS, "[{\"image\":\"" + OURS + "\"},{\"image\":\"" + SMALL + "\"},{\"image\":\"" + GOOGLE + "\"}]", null);
        stored(OURS, "places/p1/a.webp", jpeg(1000));
        stored(SMALL, "places/p1/b.webp", jpeg(100));
        when(storageService.extractObjectKey(GOOGLE)).thenReturn(null);
        when(caesiumClient.compressToWebp(any(), anyInt(), anyInt()))
                .thenAnswer(call -> ((byte[]) call.getArgument(0)).length == 1000 ? new byte[300] : new byte[95]);
        when(placeRepository.recordImageCompression(eq(placeId), anyString())).thenReturn(true);

        PlaceImageCompressionResponse response = service.compress(placeId, new CompressPlaceImagesRequest());

        assertThat(response.getStatus()).isEqualTo(PlaceImageCompressionStatus.COMPRESSED);
        assertThat(response.getImageCount()).isEqualTo(3);
        assertThat(response.getItems()).extracting(PlaceImageCompressionResponse.Item::getOutcome)
                .containsExactly(PlaceImageCompressionOutcome.COMPRESSED,
                        PlaceImageCompressionOutcome.NO_SAVINGS,
                        PlaceImageCompressionOutcome.EXTERNAL);
        assertThat(response.getOriginalBytes()).isEqualTo(1000);
        assertThat(response.getCompressedBytes()).isEqualTo(300);
        verify(storageService).uploadBytes(any(), eq("places/p1/a.webp"), eq("image/webp"));
        verify(storageService, never()).uploadBytes(any(), eq("places/p1/b.webp"), anyString());
        verify(placeRepository).recordImageCompression(eq(placeId), anyString());
    }

    @Test
    void dryRunWritesNothing() {
        place(OURS, "[]", null);
        stored(OURS, "places/p1/a.webp", jpeg(1000));
        when(caesiumClient.compressToWebp(any(), anyInt(), anyInt())).thenReturn(new byte[300]);
        CompressPlaceImagesRequest request = new CompressPlaceImagesRequest();
        request.setDryRun(true);

        PlaceImageCompressionResponse response = service.compress(placeId, request);

        assertThat(response.getStatus()).isEqualTo(PlaceImageCompressionStatus.PREVIEW);
        assertThat(response.getItems().get(0).getOutcome()).isEqualTo(PlaceImageCompressionOutcome.WOULD_COMPRESS);
        verify(storageService, never()).uploadBytes(any(), anyString(), anyString());
        verify(placeRepository, never()).recordImageCompression(any(), anyString());
    }

    @Test
    void aFailedImageLeavesThePlacePendingForTheNextRun() {
        place(OURS, "[{\"image\":\"" + SMALL + "\"}]", null);
        stored(OURS, "places/p1/a.webp", jpeg(1000));
        stored(SMALL, "places/p1/b.webp", jpeg(2000));
        when(caesiumClient.compressToWebp(any(), anyInt(), anyInt())).thenAnswer(call -> {
            if (((byte[]) call.getArgument(0)).length == 2000) {
                throw new IllegalStateException("caesium down");
            }
            return new byte[300];
        });

        PlaceImageCompressionResponse response = service.compress(placeId, new CompressPlaceImagesRequest());

        assertThat(response.getStatus()).isEqualTo(PlaceImageCompressionStatus.INCOMPLETE);
        assertThat(response.getItems().get(1).getMessage()).isEqualTo("caesium down");
        verify(placeRepository, never()).recordImageCompression(any(), anyString());
    }

    @Test
    void skipsFilesAnotherPathAlreadyCompressed() {
        place(OURS, "[{\"image\":\"" + SMALL + "\"}]", null);
        stored(OURS, "places/p1/a.webp", jpeg(1000));
        stored(SMALL, "places/p1/b.webp", jpeg(1000));
        when(imageCompressionRepository.findRecordedKeys(any())).thenReturn(Set.of("places/p1/b.webp"));
        when(caesiumClient.compressToWebp(any(), anyInt(), anyInt())).thenReturn(new byte[300]);
        when(placeRepository.recordImageCompression(eq(placeId), anyString())).thenReturn(true);

        PlaceImageCompressionResponse response = service.compress(placeId, new CompressPlaceImagesRequest());

        assertThat(response.getItems()).extracting(PlaceImageCompressionResponse.Item::getOutcome)
                .containsExactly(PlaceImageCompressionOutcome.COMPRESSED, PlaceImageCompressionOutcome.ALREADY_COMPRESSED);
        verify(storageService, never()).readObject("places/p1/b.webp");
        verify(imageCompressionRepository).save(any());
    }

    @Test
    void refusesAPlaceAlreadyCompressed() {
        place(OURS, "[]", "{\"quality\":75}");

        assertThatThrownBy(() -> service.compress(placeId, new CompressPlaceImagesRequest()))
                .isInstanceOf(BusinessException.class);
        verify(caesiumClient, never()).compressToWebp(any(), anyInt(), anyInt());
    }

    @Test
    void previewRefusesAUrlThatIsNotThePlaces() {
        place(OURS, "[]", null);
        when(storageService.extractObjectKey(SMALL)).thenReturn("places/p1/b.webp");

        assertThatThrownBy(() -> service.preview(placeId, SMALL, null, null))
                .isInstanceOf(BusinessException.class);
        verify(storageService, never()).readObject(anyString());
    }

    private void place(String thumbnail, String images, String record) {
        PlaceImageState state = new PlaceImageState();
        state.setId(placeId);
        state.setPlaceId("p1");
        state.setThumbnail(thumbnail);
        state.setImages(images);
        state.setImageCompression(record);
        when(placeRepository.findImageState(placeId)).thenReturn(Optional.of(state));
    }

    /** Bytes that start like a JPEG, so the compressor treats them as an image. */
    private static byte[] jpeg(int length) {
        byte[] bytes = new byte[length];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;
        return bytes;
    }

    private void stored(String url, String key, byte[] bytes) {
        when(storageService.extractObjectKey(url)).thenReturn(key);
        when(storageService.readObject(key)).thenReturn(bytes);
    }
}
