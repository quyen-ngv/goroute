package com.ds.goroute.type;

/**
 * Why a reviewer turned a meetup down. The host sees the label and the reviewer's note; a note is
 * required for {@code OTHER}. The labels here are used in notification text only (Vietnamese for
 * Vietnamese readers, English for everyone else); the app and the console carry their own copy.
 */
public enum MeetupRejectReason {
    NOT_A_REAL_ACTIVITY("Không phải một hoạt động thật", "Not a real activity"),
    DATING_OR_SEXUAL("Mang tính hẹn hò hoặc tình dục", "Dating or sexual content"),
    COMMERCIAL_OR_PROMOTION("Quảng cáo hoặc kinh doanh", "Commercial or promotional"),
    CONTACT_INFO("Chứa thông tin liên hệ", "Contains contact details"),
    UNSAFE_LOCATION("Địa điểm không an toàn", "Unsafe location"),
    ILLEGAL_OR_DANGEROUS("Trái pháp luật hoặc nguy hiểm", "Illegal or dangerous"),
    SENSITIVE_OR_POLITICAL("Nội dung nhạy cảm hoặc chính trị", "Sensitive or political"),
    DUPLICATE_OR_SPAM("Trùng lặp hoặc spam", "Duplicate or spam"),
    INCORRECT_DETAILS("Thông tin chưa chính xác", "Incorrect details"),
    OTHER("Lý do khác", "Other reason");

    private final String labelVi;
    private final String labelEn;

    MeetupRejectReason(String labelVi, String labelEn) {
        this.labelVi = labelVi;
        this.labelEn = labelEn;
    }

    /** The label in Vietnamese for a Vietnamese reader, otherwise in English. */
    public String label(String language) {
        return "vi".equals(language) ? labelVi : labelEn;
    }
}
