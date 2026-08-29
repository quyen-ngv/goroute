package com.ds.goroute.service.moderation;

import com.ds.goroute.config.OpenAiImageModerationProperties;
import com.ds.goroute.type.ModerationCategory;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiImageModerationProviderTest {

    @Test
    void sendsAnImageToOpenAiAndMapsOnlySupportedImageCategories() {
        OpenAiImageModerationProperties properties = properties();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiImageModerationProvider provider = new OpenAiImageModerationProvider(properties, builder);

        server.expect(requestTo("https://api.openai.com/v1/moderations"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("omni-moderation-latest"))
                .andExpect(jsonPath("$.input[0].type").value("image_url"))
                .andExpect(jsonPath("$.input[0].image_url.url").value(
                        org.hamcrest.Matchers.startsWith("data:image/jpeg;base64,")))
                .andRespond(withSuccess("""
                        {"results":[{"category_scores":{
                          "sexual":0.86,
                          "violence":0.42,
                          "violence/graphic":0.91,
                          "illicit":0.99,
                          "hate":0.99
                        }}]}
                        """, MediaType.APPLICATION_JSON));

        Map<ModerationCategory, Double> scores = provider.score(new byte[]{1, 2, 3}, "image/jpeg");

        assertThat(scores)
                .containsEntry(ModerationCategory.SEXUAL, 0.86d)
                .containsEntry(ModerationCategory.VIOLENCE, 0.91d)
                .doesNotContainKeys(ModerationCategory.DRUGS_WEAPONS, ModerationCategory.HATE_SYMBOL,
                        ModerationCategory.SPAM);
        server.verify();
    }

    @Test
    void rejectsAProviderConfigurationWithoutAnApiKey() {
        OpenAiImageModerationProperties properties = properties();
        properties.setApiKey(" ");
        OpenAiImageModerationProvider provider = new OpenAiImageModerationProvider(properties, RestClient.builder());

        assertThatThrownBy(() -> provider.score(new byte[]{1}, "image/jpeg"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OPENAI_API_KEY");
    }

    private OpenAiImageModerationProperties properties() {
        OpenAiImageModerationProperties properties = new OpenAiImageModerationProperties();
        properties.setApiKey("test-key");
        properties.setUrl("https://api.openai.com/v1/moderations");
        properties.setModel("omni-moderation-latest");
        return properties;
    }
}
