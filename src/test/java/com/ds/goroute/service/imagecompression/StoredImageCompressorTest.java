package com.ds.goroute.service.imagecompression;

import com.ds.goroute.entity.ImageCompressionRecord;
import com.ds.goroute.repository.AiTripRepository;
import com.ds.goroute.repository.ImageCompressionRepository;
import com.ds.goroute.service.StorageService;
import com.ds.goroute.thirdparty.caesium.CaesiumClient;
import com.ds.goroute.type.ImageCompressionGroup;
import com.ds.goroute.type.ImageCompressionOutcome;
import com.ds.goroute.type.ImageCompressionSource;
import com.ds.goroute.type.ImageCompressionTier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StoredImageCompressorTest {

    private static final String KEY = "expenses/" + UUID.randomUUID() + "/a.jpg";

    private final StorageService storageService = mock(StorageService.class);
    private final CaesiumClient caesiumClient = mock(CaesiumClient.class);
    private final ImageCompressionRepository repository = mock(ImageCompressionRepository.class);
    private final StoredImageCompressor compressor = new StoredImageCompressor(storageService, caesiumClient, repository);

    @Test
    void overwritesTheSameKeyAndRecordsIt() {
        when(caesiumClient.compressToWebp(any(), eq(70), eq(1600))).thenReturn(new byte[300]);

        StoredImageCompressor.Result result = compressor.compressBytes(request(false), jpeg(1000));

        assertThat(result.outcome()).isEqualTo(ImageCompressionOutcome.COMPRESSED);
        verify(storageService).uploadBytes(any(), eq(KEY), eq("image/webp"));
        ArgumentCaptor<ImageCompressionRecord> saved = ArgumentCaptor.forClass(ImageCompressionRecord.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getOutcome()).isEqualTo("COMPRESSED");
        assertThat(saved.getValue().getTier()).isEqualTo("FREE");
        assertThat(saved.getValue().getCompressedBytes()).isEqualTo(300);
    }

    @Test
    void keepsTheFileWhenTheSavingIsBelowTheThreshold() {
        when(caesiumClient.compressToWebp(any(), anyInt(), anyInt())).thenReturn(new byte[950]);

        StoredImageCompressor.Result result = compressor.compressBytes(request(false), jpeg(1000));

        assertThat(result.outcome()).isEqualTo(ImageCompressionOutcome.NO_SAVINGS);
        verify(storageService, never()).uploadBytes(any(), anyString(), anyString());
        verify(repository).save(any());
    }

    @Test
    void neverSendsAnAnimatedWebpToCaesium() {
        byte[] animated = new byte[64];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, animated, 0, 4);
        System.arraycopy("WEBPVP8X".getBytes(StandardCharsets.US_ASCII), 0, animated, 8, 8);
        animated[20] = 0x02;

        StoredImageCompressor.Result result = compressor.compressBytes(request(false), animated);

        assertThat(result.outcome()).isEqualTo(ImageCompressionOutcome.NOT_IMAGE);
        verifyNoInteractions(caesiumClient);
    }

    @Test
    void aDryRunWritesAndRecordsNothing() {
        when(caesiumClient.compressToWebp(any(), anyInt(), anyInt())).thenReturn(new byte[300]);

        StoredImageCompressor.Result result = compressor.compressBytes(request(true), jpeg(1000));

        assertThat(result.outcome()).isEqualTo(ImageCompressionOutcome.WOULD_COMPRESS);
        verify(storageService, never()).uploadBytes(any(), anyString(), anyString());
        verify(repository, never()).save(any());
    }

    @Test
    void aFailureIsNotRecordedSoTheNextRunRetries() {
        when(caesiumClient.compressToWebp(any(), anyInt(), anyInt())).thenThrow(new IllegalStateException("down"));

        StoredImageCompressor.Result result = compressor.compressBytes(request(false), jpeg(1000));

        assertThat(result.outcome()).isEqualTo(ImageCompressionOutcome.FAILED);
        assertThat(result.message()).isEqualTo("down");
        verify(repository, never()).save(any());
    }

    @Test
    void tierFollowsTheUserInTheKeyAndDefaultsToPro() {
        AiTripRepository aiTripRepository = mock(AiTripRepository.class);
        UUID freeUser = UUID.randomUUID();
        UUID proUser = UUID.randomUUID();
        when(aiTripRepository.getSubscriptionTier(freeUser)).thenReturn(null);
        when(aiTripRepository.getSubscriptionTier(proUser)).thenReturn("PRO");
        ImageOwnerTierResolver resolver = new ImageOwnerTierResolver(aiTripRepository);

        assertThat(resolver.forKey(ImageCompressionGroup.USER_UPLOADS, "expenses/" + freeUser + "/x.jpg"))
                .isEqualTo(ImageCompressionTier.FREE);
        assertThat(resolver.forKey(ImageCompressionGroup.AVATARS, "avatars/" + proUser + "/x.jpg"))
                .isEqualTo(ImageCompressionTier.PRO);
        assertThat(resolver.forKey(ImageCompressionGroup.PLACES, "places/p1/x.webp"))
                .isEqualTo(ImageCompressionTier.PRO);
        assertThat(resolver.forKey(ImageCompressionGroup.USER_UPLOADS, "expenses/not-a-uuid/x.jpg"))
                .isEqualTo(ImageCompressionTier.PRO);
        assertThat(ImageCompressionGroup.forKey("partners/verification/o1/licence.jpg")).isEmpty();
    }

    private static StoredImageCompressor.Request request(boolean dryRun) {
        return new StoredImageCompressor.Request(KEY, ImageCompressionGroup.USER_UPLOADS, ImageCompressionTier.FREE,
                70, 1600, 10, ImageCompressionSource.BULK, dryRun);
    }

    private static byte[] jpeg(int length) {
        byte[] bytes = new byte[length];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;
        return bytes;
    }
}
