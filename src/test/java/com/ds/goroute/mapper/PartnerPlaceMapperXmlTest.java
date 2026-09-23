package com.ds.goroute.mapper;

import com.ds.goroute.type.PlaceVisibilityStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The literals this insert writes into an enum-typed column.
 *
 * <p>Worth a test because the failure is invisible until much later and nowhere near the
 * insert: the row saves fine, and then MyBatis throws on every subsequent *read* of that
 * place, because {@code PlaceVisibilityStatus} has no such constant. That is what broke
 * finishing the stay wizard — {@code partnerCreateHotel} reads back the place the wizard
 * has just created.
 */
class PartnerPlaceMapperXmlTest {

    private static final Path XML =
            Path.of("src/main/resources/mapper/PartnerPlaceMapper.xml");

    private static final Pattern INSERT_CANONICAL = Pattern.compile(
            "<insert id=\"insertCanonical\">\\s*INSERT INTO places\\(([^)]+)\\)\\s*VALUES\\((.+?)\\)\\s*</insert>",
            Pattern.DOTALL);

    @Test
    @DisplayName("writes a visibility_status the enum can read back")
    void visibilityStatusIsAKnownConstant() throws IOException {
        String literal = literalFor("visibility_status");

        assertThat(PlaceVisibilityStatus.values())
                .as("visibility_status literal '%s' in insertCanonical", literal)
                .anyMatch(status -> status.name().equals(literal));
    }

    /** The literal written into one column, with its quotes removed. */
    private String literalFor(String column) throws IOException {
        String xml = Files.readString(XML, StandardCharsets.UTF_8);
        Matcher matcher = INSERT_CANONICAL.matcher(xml);
        assertThat(matcher.find()).as("insertCanonical in %s", XML).isTrue();

        List<String> columns = split(matcher.group(1));
        List<String> values = split(matcher.group(2));
        assertThat(values).as("one value per column").hasSameSizeAs(columns);

        int index = columns.indexOf(column);
        assertThat(index).as("column %s", column).isNotNegative();

        String value = values.get(index);
        assertThat(value)
                .as("%s should be a literal, not a parameter", column)
                .startsWith("'")
                .endsWith("'");
        return value.substring(1, value.length() - 1);
    }

    private static List<String> split(String list) {
        return Arrays.stream(list.split(",")).map(String::trim).toList();
    }
}
