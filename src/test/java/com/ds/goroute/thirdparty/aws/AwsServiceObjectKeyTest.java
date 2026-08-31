package com.ds.goroute.thirdparty.aws;

import com.ds.goroute.config.AwsProperties;
import com.ds.goroute.config.RemoteFileDownloadProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the one function that decides whether deleting an image deletes anything.
 *
 * <p>{@code extractObjectKey} answers {@code null} for a URL it does not recognise, and
 * every delete path treats {@code null} as "nothing to do". A configuration change that
 * stops the product's own URLs matching therefore turns every image delete into a no-op
 * without an error anywhere. These cases pin the URL shapes actually stored in the
 * database against the configuration that produced them.
 */
class AwsServiceObjectKeyTest {

    private static AwsService serviceWith(String bucket, String cloudfrontDomain, String endpoint) {
        AwsProperties properties = new AwsProperties();
        properties.setS3BucketName(bucket);
        properties.setCloudfrontDomain(cloudfrontDomain);
        properties.setEndpoint(endpoint);
        return new AwsService(properties, new RemoteFileDownloadProperties());
    }

    @Nested
    @DisplayName("MinIO behind nginx at /resource/<bucket>/")
    class BehindNginx {

        private final AwsService service = serviceWith(
                "vietdetour", "onestudy.id.vn/resource/vietdetour", "http://minio:9000");

        @Test
        void keepsTheKeyItUploadedUnder() {
            assertThat(service.extractObjectKey(
                    "https://onestudy.id.vn/resource/vietdetour/expenses/user-1/photo.webp"))
                    .isEqualTo("expenses/user-1/photo.webp");
        }

        @Test
        void acceptsTheLegacyGorouteResourcePath() {
            assertThat(service.extractObjectKey(
                    "https://onestudy.id.vn/resource/goroute/avatars/user-1/a.png"))
                    .isEqualTo("avatars/user-1/a.png");
        }

        @Test
        void acceptsTheInternalEndpointUrl() {
            assertThat(service.extractObjectKey(
                    "http://minio:9000/vietdetour/city-stories/s.jpg"))
                    .isEqualTo("city-stories/s.jpg");
        }

        @Test
        void acceptsABareKey() {
            assertThat(service.extractObjectKey("expenses/user-1/photo.webp"))
                    .isEqualTo("expenses/user-1/photo.webp");
        }

        @Test
        void refusesAForeignHost() {
            assertThat(service.extractObjectKey(
                    "https://lh3.googleusercontent.com/p/abc=w400")).isNull();
        }

        @Test
        void refusesBlankInput() {
            assertThat(service.extractObjectKey(null)).isNull();
            assertThat(service.extractObjectKey("   ")).isNull();
        }
    }

    @Nested
    @DisplayName("Cloudfront domain configured with an explicit scheme")
    class WithScheme {

        private final AwsService service = serviceWith(
                "goroute", "https://cdn.example.com", "");

        @Test
        void stripsOnlyTheLeadingSlash() {
            assertThat(service.extractObjectKey("https://cdn.example.com/foods/pho.webp"))
                    .isEqualTo("foods/pho.webp");
        }

        @Test
        void refusesAnotherCdnHost() {
            assertThat(service.extractObjectKey("https://other.example.com/foods/pho.webp"))
                    .isNull();
        }
    }

    @Nested
    @DisplayName("No cloudfront domain: URLs carry the bucket host")
    class BucketHost {

        private final AwsService service = serviceWith("goroute", "", "");

        @Test
        void acceptsTheVirtualHostedBucketUrl() {
            assertThat(service.extractObjectKey(
                    "https://goroute.s3.ap-southeast-1.amazonaws.com/places/p.jpg"))
                    .isEqualTo("places/p.jpg");
        }
    }

    @Nested
    @DisplayName("Bucket renamed without re-writing stored URLs")
    class BucketRenamed {

        /**
         * The failure this whole test class exists for. Objects were written while the
         * bucket was {@code vietdetour} and their URLs are still in the database; the
         * configuration now names a different bucket and a different domain, so the key
         * no longer resolves and every delete against those rows quietly does nothing.
         */
        @Test
        void stopsResolvingOldUrls() {
            AwsService renamed = serviceWith("goroute", "cdn.example.com", "");

            assertThat(renamed.extractObjectKey(
                    "https://onestudy.id.vn/resource/vietdetour/expenses/user-1/photo.webp"))
                    .as("legacy host is still recognised, so the key is bucket-relative")
                    .isEqualTo("vietdetour/expenses/user-1/photo.webp");

            assertThat(renamed.extractObjectKey(
                    "https://old-cdn.example.net/expenses/user-1/photo.webp"))
                    .as("an unrecognised host resolves to nothing and deletes nothing")
                    .isNull();
        }
    }
}
