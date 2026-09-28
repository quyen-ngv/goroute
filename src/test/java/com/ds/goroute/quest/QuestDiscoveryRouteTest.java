package com.ds.goroute.quest;

import com.ds.goroute.quest.domain.QuestCheckpoint;
import com.ds.goroute.quest.domain.QuestListItem;
import com.ds.goroute.quest.domain.QuestVersion;
import com.ds.goroute.quest.dto.QuestPublicDetailResponse;
import com.ds.goroute.quest.persistence.QuestRepository;
import com.ds.goroute.quest.service.QuestDiscoveryService;
import com.ds.goroute.service.marketplace.MarketplaceJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("The detail page shows the whole route only when the creator chose to")
class QuestDiscoveryRouteTest {

    private static final BigDecimal SPOT_LAT = new BigDecimal("21.0288000");
    private static final BigDecimal SPOT_LNG = new BigDecimal("105.8524000");
    private static final BigDecimal CENTER_LAT = new BigDecimal("21.0290000");
    private static final BigDecimal CENTER_LNG = new BigDecimal("105.8530000");

    private final QuestRepository repository = mock(QuestRepository.class);
    private final QuestDiscoveryService service =
            new QuestDiscoveryService(repository, new MarketplaceJson(new ObjectMapper()));
    private final UUID questId = UUID.randomUUID();
    private final UUID versionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        QuestCheckpoint pin = QuestCheckpoint.builder().id(UUID.randomUUID()).sortOrder(0).name("Gate")
                .category("GATE").latitude(SPOT_LAT).longitude(SPOT_LNG).radiusM(40).build();
        QuestCheckpoint area = QuestCheckpoint.builder().id(UUID.randomUUID()).sortOrder(1).name("Hidden stele")
                .latitude(SPOT_LAT).longitude(SPOT_LNG).radiusM(30).findMode("AREA")
                .searchCenterLat(CENTER_LAT).searchCenterLng(CENTER_LNG).searchRadiusM(200).build();
        when(repository.loadVersionGraph(versionId)).thenReturn(Optional.of(QuestVersion.builder()
                .id(versionId).questId(questId).checkpoints(List.of(pin, area)).build()));
    }

    private void published(boolean revealRoute) {
        when(repository.findPublicQuestDetail(questId)).thenReturn(Optional.of(QuestListItem.builder()
                .questId(questId).publishedVersionId(versionId).title("Old Quarter")
                .checkpointCount(2).revealRoute(revealRoute).build()));
    }

    @Test
    @DisplayName("shown (the default): a PIN at its spot, an AREA only by its search circle")
    void shownRoute() {
        published(true);

        QuestPublicDetailResponse detail = service.detail(questId);

        assertThat(detail.revealRoute()).isTrue();
        assertThat(detail.route()).hasSize(2);
        QuestPublicDetailResponse.RouteStop gate = detail.route().get(0);
        assertThat(gate.name()).isEqualTo("Gate");
        assertThat(gate.findMode()).isEqualTo("PIN");
        assertThat(gate.latitude()).isEqualByComparingTo(SPOT_LAT);
        assertThat(gate.radiusM()).isEqualTo(40);
        QuestPublicDetailResponse.RouteStop stele = detail.route().get(1);
        assertThat(stele.findMode()).isEqualTo("AREA");
        assertThat(stele.latitude()).as("never an AREA's real spot").isEqualByComparingTo(CENTER_LAT);
        assertThat(stele.longitude()).isEqualByComparingTo(CENTER_LNG);
        assertThat(stele.radiusM()).isEqualTo(200);
    }

    @Test
    @DisplayName("hidden: no route, and the checkpoints are not even loaded")
    void hiddenRoute() {
        published(false);

        QuestPublicDetailResponse detail = service.detail(questId);

        assertThat(detail.revealRoute()).isFalse();
        assertThat(detail.route()).isEmpty();
        verify(repository, never()).loadVersionGraph(any());
    }

    @Test
    @DisplayName("a quest listed before the option existed shows its route")
    void defaultIsShown() {
        assertThat(new QuestListItem().isRevealRoute()).isTrue();
        assertThat(QuestVersion.builder().build().isRevealRoute()).isTrue();
    }
}
