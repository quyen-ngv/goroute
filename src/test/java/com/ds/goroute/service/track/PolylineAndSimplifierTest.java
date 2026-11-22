package com.ds.goroute.service.track;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PolylineAndSimplifierTest {

    @Test
    void encodesGoogleReferenceVector() {
        List<double[]> points = List.of(
                new double[] {38.5, -120.2},
                new double[] {40.7, -120.95},
                new double[] {43.252, -126.453});

        assertThat(PolylineCodec.encode(points)).isEqualTo("_p~iF~ps|U_ulLnnqC_mqNvxq`@");
    }

    @Test
    void decodesWhatItEncodes() {
        List<double[]> points = List.of(
                new double[] {11.94146, 108.44012},
                new double[] {11.94290, 108.43720},
                new double[] {-33.86785, 151.20732});

        List<double[]> decoded = PolylineCodec.decode(PolylineCodec.encode(points));

        assertThat(decoded).hasSize(3);
        for (int i = 0; i < points.size(); i++) {
            assertThat(decoded.get(i)[0]).isCloseTo(points.get(i)[0], within(1e-5));
            assertThat(decoded.get(i)[1]).isCloseTo(points.get(i)[1], within(1e-5));
        }
    }

    @Test
    void straightLineCollapsesToItsEnds() {
        List<double[]> line = new ArrayList<>();
        for (int i = 0; i <= 100; i++) {
            line.add(new double[] {11.94 + i * 0.0001, 108.44});
        }

        List<double[]> simplified = LineSimplifier.simplify(line, 1500);

        assertThat(simplified).hasSize(2);
        assertThat(simplified.get(0)).isSameAs(line.get(0));
        assertThat(simplified.get(1)).isSameAs(line.get(100));
    }

    @Test
    void keepsACornerAndRespectsThePointBudget() {
        List<double[]> corner = List.of(
                new double[] {11.9400, 108.4400},
                new double[] {11.9450, 108.4400},
                new double[] {11.9450, 108.4450});
        assertThat(LineSimplifier.simplify(corner, 1500)).hasSize(3);

        List<double[]> zigzag = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            zigzag.add(new double[] {11.94 + i * 0.0001, 108.44 + (i % 2 == 0 ? 0 : 0.001)});
        }
        assertThat(LineSimplifier.simplify(zigzag, 100)).hasSizeLessThanOrEqualTo(100);
    }
}
