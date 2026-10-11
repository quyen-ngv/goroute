package com.ds.goroute.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class MapTileTest {

    @Test
    void theZoomZeroTileIsTheWholeMercatorWorld() {
        MapTile world = new MapTile(0, 0, 0);

        assertThat(world.west()).isEqualTo(-180.0);
        assertThat(world.east()).isEqualTo(180.0);
        assertThat(world.north()).isCloseTo(MapTile.MAX_LATITUDE, within(1e-6));
        assertThat(world.south()).isCloseTo(-MapTile.MAX_LATITUDE, within(1e-6));
    }

    @Test
    void aPointLiesInsideTheTileThatContainsIt() {
        double latitude = 21.0287;
        double longitude = 105.8524;

        for (int z = 3; z <= 18; z++) {
            MapTile tile = MapTile.containing(latitude, longitude, z);

            assertThat(latitude).isBetween(tile.south(), tile.north());
            assertThat(longitude).isBetween(tile.west(), tile.east());
        }
    }

    @Test
    void aTileHalvesIntoTheFourTilesOneZoomCloser() {
        MapTile parent = MapTile.containing(16.0544, 108.2022, 9);
        MapTile child = MapTile.containing(16.0544, 108.2022, 10);

        assertThat(child.x() >> 1).isEqualTo(parent.x());
        assertThat(child.y() >> 1).isEqualTo(parent.y());
    }

    @Test
    void rejectsTilesOutsideTheirZoom() {
        assertThat(MapTile.isValid(3, 7, 7)).isTrue();
        assertThat(MapTile.isValid(3, 8, 0)).isFalse();
        assertThat(MapTile.isValid(3, 0, -1)).isFalse();
        assertThat(MapTile.isValid(23, 0, 0)).isFalse();
        assertThatThrownBy(() -> new MapTile(2, 4, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
