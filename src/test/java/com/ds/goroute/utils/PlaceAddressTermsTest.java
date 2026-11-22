package com.ds.goroute.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PlaceAddressTerms")
class PlaceAddressTermsTest {

    @Test
    @DisplayName("should split a Hà Nội address into house number, street pair and district pair")
    void splitsAStreetAddress() {
        PlaceAddressTerms.Terms terms = PlaceAddressTerms.of("40 P. Nhà Chung, Hoàn Kiếm, Hà Nội, Vietnam", List.of());

        assertThat(terms.houseNumbers()).containsExactly("40");
        assertThat(terms.pairs()).contains("nha_chung", "hoan_kiem", "ha_noi").doesNotContain("pho_nha", "p_nha");
        assertThat(terms.singles()).contains("vietnam", "hanoi", "hoankiem");
    }

    @Test
    @DisplayName("should not pair a syllable with one from the next part of the address")
    void keepsPartsApart() {
        PlaceAddressTerms.Terms terms = PlaceAddressTerms.of(
                "35A Đường Võ Trường Toản, Lâm Viên - Đà Lạt, Lâm Đồng 670000, Vietnam", List.of());

        assertThat(terms.houseNumbers()).containsExactly("35a");
        assertThat(terms.pairs())
                .contains("vo_truong", "truong_toan", "lam_vien", "da_lat", "lam_dong")
                .doesNotContain("toan_lam", "vien_da", "lat_lam", "dong_670000");
        assertThat(terms.singles()).contains("dalat").doesNotContain("670000");
    }

    @Test
    @DisplayName("should keep a unit word that numbers the unit")
    void keepsNumberedUnits() {
        PlaceAddressTerms.Terms terms = PlaceAddressTerms.of("12 Lê Lợi, Q. 1, P. 5, Hồ Chí Minh", List.of());

        assertThat(terms.pairs()).contains("le_loi", "quan_1", "phuong_5", "ho_chi", "chi_minh");
    }

    @Test
    @DisplayName("should add the ward and province names, and the usual short names of the province")
    void addsAreaNames() {
        PlaceAddressTerms.Terms terms = PlaceAddressTerms.of(
                "7 Huỳnh Thúc Kháng", List.of("Sài Gòn", "Hồ Chí Minh"));

        assertThat(terms.pairs()).contains("huynh_thuc", "thuc_khang", "sai_gon", "ho_chi", "chi_minh");
        assertThat(terms.singles()).contains("saigon", "hcm", "tphcm");
    }

    @Test
    @DisplayName("should leave out Google plus codes and read an address with no house number")
    void ignoresPlusCodes() {
        PlaceAddressTerms.Terms terms = PlaceAddressTerms.of(
                "WCQV+9W2, Xuân Hương - Đà Lạt, Lâm Đồng, Vietnam", List.of());

        assertThat(terms.houseNumbers()).isEmpty();
        assertThat(terms.pairs()).contains("xuan_huong", "da_lat").noneMatch(pair -> pair.contains("wcqv"));
    }

    @Test
    @DisplayName("should return nothing for a missing address")
    void emptyForNoAddress() {
        PlaceAddressTerms.Terms terms = PlaceAddressTerms.of(null, null);

        assertThat(terms.pairs()).isEmpty();
        assertThat(terms.singles()).isEmpty();
        assertThat(terms.houseNumbers()).isEmpty();
    }

    @Test
    @DisplayName("should take a unit word as one only where a name follows it")
    void findsUnitWords() {
        assertThat(PlaceAddressTerms.markers(LuceneKeywordQuery.words("phở mai phố nhà chung", 8)))
                .containsExactly(false, false, true, false, false);
        assertThat(PlaceAddressTerms.markers(LuceneKeywordQuery.words("phở mai phường cửa nam", 8)))
                .containsExactly(false, false, true, false, false);
        assertThat(PlaceAddressTerms.markers(LuceneKeywordQuery.words("cafe thành phố huế", 8)))
                .containsExactly(false, true, true, false);
        assertThat(PlaceAddressTerms.markers(LuceneKeywordQuery.words("phở quận 1", 8)))
                .containsExactly(false, false, false);
        assertThat(PlaceAddressTerms.markers(LuceneKeywordQuery.words("quán phở", 8)))
                .containsExactly(false, false);
        assertThat(PlaceAddressTerms.markers(LuceneKeywordQuery.words("hà nội phố", 8)))
                .containsExactly(false, false, false);
    }
}
