package com.ds.goroute.service.moderation;

import com.ds.goroute.entity.ModerationTerm;
import com.ds.goroute.type.ModerationAction;
import com.ds.goroute.type.ModerationCategory;
import com.ds.goroute.utils.ModerationTextNormalizer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The matcher is where the filter is either useful or a source of false blocks, so the
 * evasion tricks and the false-positive traps are both pinned down here.
 */
class ModerationTermIndexTest {

    private final ModerationTermIndex index = ModerationTermIndex.of(List.of(
            term("khỏa thân", ModerationCategory.SEXUAL, ModerationAction.FLAG, false),
            term("gái gọi", ModerationCategory.SEXUAL, ModerationAction.BLOCK, false),
            term("dcm", ModerationCategory.HARASSMENT, ModerationAction.FLAG, false),
            term("lừa đảo", ModerationCategory.SCAM, ModerationAction.FLAG, false),
            term("tượng khỏa thân", ModerationCategory.SEXUAL, ModerationAction.ALLOW, true),
            term("lừa đảo du lịch", ModerationCategory.SCAM, ModerationAction.ALLOW, true)));

    @Test
    void matchesDespiteMissingVietnameseDiacritics() {
        assertThat(index.evaluate("o day co gai goi").action()).isEqualTo(ModerationAction.BLOCK);
    }

    @Test
    void matchesDespiteInsertedSeparators() {
        assertThat(index.evaluate("g.a.i g-o-i").action()).isEqualTo(ModerationAction.BLOCK);
    }

    @Test
    void matchesDespiteLookalikeDigits() {
        assertThat(index.evaluate("g41 g01").action()).isEqualTo(ModerationAction.BLOCK);
    }

    @Test
    void matchesSpacedOutSingleWords() {
        assertThat(index.evaluate("thang nay d c m that").action()).isEqualTo(ModerationAction.FLAG);
    }

    @Test
    void matchesRegardlessOfCase() {
        assertThat(index.evaluate("GÁI GỌI").action()).isEqualTo(ModerationAction.BLOCK);
    }

    /**
     * The reason the exemption list exists. Museums and galleries are ordinary travel
     * writing, and blocking them costs a user for nothing.
     */
    @Test
    void exemptPhraseSuppressesTheTermInsideIt() {
        assertThat(index.evaluate("Bảo tàng có một tượng khỏa thân rất đẹp").isAllowed()).isTrue();
    }

    /** An exemption suppresses only its own span, not the rest of the passage. */
    @Test
    void exemptPhraseDoesNotSuppressTheSameTermElsewhere() {
        ModerationVerdict verdict = index.evaluate("Có tượng khỏa thân, và ảnh khỏa thân của chủ quán");

        assertThat(verdict.action()).isEqualTo(ModerationAction.FLAG);
        assertThat(verdict.category()).isEqualTo(ModerationCategory.SEXUAL);
    }

    @Test
    void honestComplaintsAreOnlyFlaggedNeverBlocked() {
        ModerationVerdict verdict = index.evaluate("Quán này lừa đảo khách du lịch");

        assertThat(verdict.action()).isEqualTo(ModerationAction.FLAG);
    }

    @Test
    void exemptWarningPhraseIsAllowedEntirely() {
        assertThat(index.evaluate("Cảnh báo lừa đảo du lịch ở khu này").isAllowed()).isTrue();
    }

    @Test
    void ordinaryTravelWritingIsUntouched() {
        assertThat(index.evaluate("Đèo Hải Vân buổi sáng rất đẹp, cà phê ở đây ngon").isAllowed()).isTrue();
        assertThat(index.evaluate("Bún đậu mắm tôm quán bà Béo ăn được").isAllowed()).isTrue();
    }

    @Test
    void blockOutranksFlagWhenBothMatch() {
        assertThat(index.evaluate("dcm ở đây toàn gái gọi").action()).isEqualTo(ModerationAction.BLOCK);
    }

    /** Short words must not fire from inside longer ones. */
    @Test
    void singleWordTermsRequireWordBoundaries() {
        assertThat(index.evaluate("dcmxyzabc là mã đơn hàng").isAllowed()).isTrue();
    }

    private ModerationTerm term(String value, ModerationCategory category,
                                ModerationAction action, boolean exemption) {
        return ModerationTerm.builder()
                .id(UUID.randomUUID())
                .term(value)
                .normalizedTerm(ModerationTextNormalizer.normalize(value))
                .category(category)
                .action(action)
                .language("vi")
                .isExemption(exemption)
                .isActive(true)
                .build();
    }
}
