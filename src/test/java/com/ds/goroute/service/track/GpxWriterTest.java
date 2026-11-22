package com.ds.goroute.service.track;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GpxWriterTest {

    @Test
    void writesOneSegmentPerDayWithUtcTimesAndEscapedName() throws Exception {
        long nine = Instant.parse("2026-10-02T02:00:00Z").toEpochMilli();
        String gpx = GpxWriter.write("Đà Lạt <3 & \"friends\"", List.of(
                List.of(new GpxWriter.Point(11.9404, 108.4583, 1502, nine),
                        new GpxWriter.Point(11.9410, 108.4590, null, nine + 5_000)),
                List.of(new GpxWriter.Point(11.95, 108.46, null, null))));

        Document document = parse(gpx);
        Element root = document.getDocumentElement();
        assertThat(root.getTagName()).isEqualTo("gpx");
        assertThat(root.getAttribute("version")).isEqualTo("1.1");
        assertThat(root.getNamespaceURI()).isEqualTo("http://www.topografix.com/GPX/1/1");
        assertThat(document.getElementsByTagNameNS("*", "name").item(0).getTextContent())
                .isEqualTo("Đà Lạt <3 & \"friends\"");
        assertThat(document.getElementsByTagNameNS("*", "trk").getLength()).isEqualTo(1);
        assertThat(document.getElementsByTagNameNS("*", "trkseg").getLength()).isEqualTo(2);

        NodeList points = document.getElementsByTagNameNS("*", "trkpt");
        assertThat(points.getLength()).isEqualTo(3);
        Element first = (Element) points.item(0);
        assertThat(first.getAttribute("lat")).isEqualTo("11.9404000");
        assertThat(first.getAttribute("lon")).isEqualTo("108.4583000");
        assertThat(first.getElementsByTagNameNS("*", "ele").item(0).getTextContent()).isEqualTo("1502");
        assertThat(first.getElementsByTagNameNS("*", "time").item(0).getTextContent())
                .isEqualTo("2026-10-02T02:00:00Z");
        Element second = (Element) points.item(1);
        assertThat(second.getElementsByTagNameNS("*", "ele").getLength()).isZero();
        assertThat(second.getElementsByTagNameNS("*", "time").item(0).getTextContent())
                .isEqualTo("2026-10-02T02:00:05Z");
        assertThat(((Element) points.item(2)).getChildNodes().getLength()).isZero();
    }

    @Test
    void controlCharactersAreDroppedSoTheDocumentStaysWellFormed() {
        assertThat(GpxWriter.escape("a\u0001b\tc'")).isEqualTo("ab\tc&apos;");
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
}
