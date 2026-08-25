package com.ds.goroute.thirdparty.aws;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemoteFileUrlPolicyTest {

    @Test
    void rejectsNonHttpsUrls() {
        assertThatThrownBy(() -> RemoteFileUrlPolicy.requirePublicHttps("http://8.8.8.8/file.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPrivateAndLinkLocalAddresses() {
        assertThatThrownBy(() -> RemoteFileUrlPolicy.requirePublicHttps("https://127.0.0.1/file.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RemoteFileUrlPolicy.requirePublicHttps("https://10.0.0.1/file.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RemoteFileUrlPolicy.requirePublicHttps("https://169.254.169.254/latest/meta-data"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsPublicHttpsAddressWithoutOpeningAConnection() {
        assertThat(RemoteFileUrlPolicy.requirePublicHttps("https://8.8.8.8/file.jpg").getHost())
                .isEqualTo("8.8.8.8");
    }

    @Test
    void onlyTreatsRedirectsToTheSameOriginAsSafeForCredentials() {
        assertThat(RemoteFileUrlPolicy.isSameOrigin(
                RemoteFileUrlPolicy.requirePublicHttps("https://8.8.8.8/source"),
                RemoteFileUrlPolicy.requirePublicHttps("https://8.8.8.8/target")))
                .isTrue();
        assertThat(RemoteFileUrlPolicy.isSameOrigin(
                RemoteFileUrlPolicy.requirePublicHttps("https://8.8.8.8/source"),
                RemoteFileUrlPolicy.requirePublicHttps("https://1.1.1.1/target")))
                .isFalse();
    }
}
