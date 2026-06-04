package com.ds.goroute.utils;

import com.ds.goroute.config.filter.AcceptLanguageFilter;

/**
 * Claude prompt language rules and localized static copy for AI trip flows.
 */
public final class AiTripLanguageSupport {

    private AiTripLanguageSupport() {
    }

    public static String currentCode() {
        return AcceptLanguageFilter.currentCode();
    }

    public static String claudeLanguageRule() {
        String code = currentCode();
        return """
                Language: Respond ONLY in %s (locale code: %s).
                All user-visible strings in JSON (reasons, tips, summaries) must use this language.
                Do not mix languages. Candidate names and addresses stay as provided in input.
                """.formatted(displayName(code), code);
    }

    public static String displayName(String code) {
        return switch (normalize(code)) {
            case "vi" -> "Vietnamese";
            case "ko" -> "Korean";
            case "th" -> "Thai";
            case "ja" -> "Japanese";
            case "zh-TW" -> "Traditional Chinese";
            case "ru" -> "Russian";
            case "hi" -> "Hindi";
            default -> "English";
        };
    }

    public static String paceLabel(String pace) {
        String p = pace == null ? "BALANCED" : pace.toUpperCase();
        String lang = currentCode();
        return switch (normalize(lang)) {
            case "vi" -> switch (p) {
                case "RELAXED" -> "Nháº¹";
                case "EAGER" -> "DÃ y";
                default -> "Vá»«a";
            };
            case "ko" -> switch (p) {
                case "RELAXED" -> "ì—¬ìœ ë¡­ê²Œ";
                case "EAGER" -> "ì•Œì°¨ê²Œ";
                default -> "ê· í˜•";
            };
            case "th" -> switch (p) {
                case "RELAXED" -> "à¸ªà¸šà¸²à¸¢à¹†";
                case "EAGER" -> "à¹à¸™à¹ˆà¸™";
                default -> "à¸žà¸­à¸”à¸µ";
            };
            case "ja" -> switch (p) {
                case "RELAXED" -> "ã‚†ã£ãã‚Š";
                case "EAGER" -> "ã—ã£ã‹ã‚Š";
                default -> "ãƒãƒ©ãƒ³ã‚¹";
            };
            case "zh-TW" -> switch (p) {
                case "RELAXED" -> "è¼•é¬†";
                case "EAGER" -> "ç·Šæ¹Š";
                default -> "å¹³è¡¡";
            };
            case "ru" -> switch (p) {
                case "RELAXED" -> "Ð¡Ð¿Ð¾ÐºÐ¾Ð¹Ð½Ñ‹Ð¹";
                case "EAGER" -> "ÐŸÐ»Ð¾Ñ‚Ð½Ñ‹Ð¹";
                default -> "Ð¡Ð±Ð°Ð»Ð°Ð½ÑÐ¸Ñ€Ð¾Ð²Ð°Ð½Ð½Ñ‹Ð¹";
            };
            case "hi" -> switch (p) {
                case "RELAXED" -> "à¤†à¤°à¤¾à¤®à¤¦à¤¾à¤¯à¤•";
                case "EAGER" -> "à¤­à¤°à¤ªà¥‚à¤°";
                default -> "à¤¸à¤‚à¤¤à¥à¤²à¤¿à¤¤";
            };
            default -> switch (p) {
                case "RELAXED" -> "Relaxed";
                case "EAGER" -> "Packed";
                default -> "Balanced";
            };
        };
    }

    public static String fallbackVisitTip(String group, String sourceType, String timeHint) {
        String lang = currentCode();
        return switch (normalize(lang)) {
            case "vi" -> fallbackVisitTipVi(group, sourceType, timeHint);
            case "ko" -> fallbackVisitTipKo(group, sourceType, timeHint);
            case "th" -> fallbackVisitTipTh(group, sourceType, timeHint);
            case "ja" -> fallbackVisitTipJa(group, sourceType, timeHint);
            case "zh-TW" -> fallbackVisitTipZhTw(group, sourceType, timeHint);
            case "ru" -> fallbackVisitTipRu(group, sourceType, timeHint);
            case "hi" -> fallbackVisitTipHi(group, sourceType, timeHint);
            default -> fallbackVisitTipEn(group, sourceType, timeHint);
        };
    }

    public static String coverageMessage(
            int selectedCount, int scheduledCount, int filledDays, int totalDays) {
        String lang = currentCode();
        if (scheduledCount == 0) {
            return coverageEmpty(lang);
        }
        if (scheduledCount < selectedCount) {
            int skipped = selectedCount - scheduledCount;
            return coveragePartial(lang, scheduledCount, selectedCount, skipped, totalDays);
        }
        if (filledDays < totalDays) {
            return coveragePartialDays(lang, scheduledCount, filledDays, totalDays);
        }
        return coverageFull(lang, filledDays, scheduledCount);
    }

    public static String tripAlreadyCreatedMessage() {
        return switch (normalize(currentCode())) {
            case "vi" -> "Chuyáº¿n Ä‘i Ä‘Ã£ Ä‘Æ°á»£c táº¡o trÆ°á»›c Ä‘Ã³.";
            case "ko" -> "ì´ë¯¸ ìƒì„±ëœ ì—¬í–‰ìž…ë‹ˆë‹¤.";
            case "th" -> "à¸ªà¸£à¹‰à¸²à¸‡à¸—à¸£à¸´à¸›à¸™à¸µà¹‰à¹„à¸›à¹à¸¥à¹‰à¸§";
            case "ja" -> "ã“ã®æ—…è¡Œã¯ã™ã§ã«ä½œæˆã•ã‚Œã¦ã„ã¾ã™ã€‚";
            case "zh-TW" -> "æ­¤è¡Œç¨‹å·²å»ºç«‹ã€‚";
            case "ru" -> "ÐŸÐ¾ÐµÐ·Ð´ÐºÐ° ÑƒÐ¶Ðµ Ð±Ñ‹Ð»Ð° ÑÐ¾Ð·Ð´Ð°Ð½Ð°.";
            case "hi" -> "à¤¯à¤¹ à¤¯à¤¾à¤¤à¥à¤°à¤¾ à¤ªà¤¹à¤²à¥‡ à¤¹à¥€ à¤¬à¤¨à¤¾à¤ˆ à¤œà¤¾ à¤šà¥à¤•à¥€ à¤¹à¥ˆà¥¤";
            default -> "Trip was already created.";
        };
    }

    private static String coverageEmpty(String lang) {
        return switch (normalize(lang)) {
            case "vi" -> "Báº¡n chÆ°a chá»n Ä‘á»‹a Ä‘iá»ƒm nÃ o, trip sáº½ Ä‘Æ°á»£c táº¡o trá»‘ng.";
            case "ko" -> "ì„ íƒí•œ ìž¥ì†Œê°€ ì—†ì–´ ë¹ˆ ì¼ì •ìœ¼ë¡œ ìƒì„±ë©ë‹ˆë‹¤.";
            case "th" -> "à¸¢à¸±à¸‡à¹„à¸¡à¹ˆà¹„à¸”à¹‰à¹€à¸¥à¸·à¸­à¸à¸ªà¸–à¸²à¸™à¸—à¸µà¹ˆ à¸—à¸£à¸´à¸›à¸ˆà¸°à¸–à¸¹à¸à¸ªà¸£à¹‰à¸²à¸‡à¸§à¹ˆà¸²à¸‡";
            case "ja" -> "å ´æ‰€ãŒé¸æŠžã•ã‚Œã¦ã„ãªã„ãŸã‚ã€ç©ºã®æ—…ç¨‹ã§ä½œæˆã•ã‚Œã¾ã™ã€‚";
            case "zh-TW" -> "å°šæœªé¸æ“‡åœ°é»žï¼Œå°‡å»ºç«‹ç©ºç™½è¡Œç¨‹ã€‚";
            case "ru" -> "ÐœÐµÑÑ‚Ð° Ð½Ðµ Ð²Ñ‹Ð±Ñ€Ð°Ð½Ñ‹ â€” Ð¼Ð°Ñ€ÑˆÑ€ÑƒÑ‚ Ð±ÑƒÐ´ÐµÑ‚ ÑÐ¾Ð·Ð´Ð°Ð½ Ð¿ÑƒÑÑ‚Ñ‹Ð¼.";
            case "hi" -> "à¤•à¥‹à¤ˆ à¤¸à¥à¤¥à¤¾à¤¨ à¤¨à¤¹à¥€à¤‚ à¤šà¥à¤¨à¤¾ â€” à¤¯à¤¾à¤¤à¥à¤°à¤¾ à¤–à¤¾à¤²à¥€ à¤¬à¤¨à¥‡à¤—à¥€à¥¤";
            default -> "No places selected â€” the trip will be created empty.";
        };
    }

    private static String coveragePartial(
            String lang, int scheduled, int selected, int skipped, int totalDays) {
        return switch (normalize(lang)) {
            case "vi" -> "ÄÃ£ xáº¿p " + scheduled + "/" + selected + " Ä‘á»‹a Ä‘iá»ƒm vÃ o lá»‹ch. "
                    + skipped + " Ä‘á»‹a Ä‘iá»ƒm khÃ´ng Ä‘á»§ thá»i gian trong " + totalDays + " ngÃ y.";
            case "ko" -> scheduled + "/" + selected + "ê³³ì„ ì¼ì •ì— ë„£ì—ˆìŠµë‹ˆë‹¤. "
                    + skipped + "ê³³ì€ " + totalDays + "ì¼ ì•ˆì— ì‹œê°„ì´ ë¶€ì¡±í•©ë‹ˆë‹¤.";
            case "th" -> "à¸ˆà¸±à¸” " + scheduled + "/" + selected + " à¹à¸«à¹ˆà¸‡à¹ƒà¸™à¸•à¸²à¸£à¸²à¸‡ "
                    + skipped + " à¹à¸«à¹ˆà¸‡à¹„à¸¡à¹ˆà¸žà¸­à¹€à¸§à¸¥à¸²à¹ƒà¸™ " + totalDays + " à¸§à¸±à¸™";
            case "ja" -> selected + "ä»¶ä¸­" + scheduled + "ä»¶ã‚’æ—…ç¨‹ã«è¿½åŠ ã€‚"
                    + skipped + "ä»¶ã¯" + totalDays + "æ—¥ã§ã¯æ™‚é–“ãŒè¶³ã‚Šã¾ã›ã‚“ã€‚";
            case "zh-TW" -> "å·²æŽ’å…¥ " + scheduled + "/" + selected + " å€‹åœ°é»žï¼Œ"
                    + skipped + " å€‹åœ¨ " + totalDays + " å¤©å…§æ™‚é–“ä¸è¶³ã€‚";
            case "ru" -> "Ð’ Ð¼Ð°Ñ€ÑˆÑ€ÑƒÑ‚ Ð´Ð¾Ð±Ð°Ð²Ð»ÐµÐ½Ð¾ " + scheduled + " Ð¸Ð· " + selected + ". "
                    + skipped + " Ð¼ÐµÑÑ‚ Ð½Ðµ Ð¿Ð¾Ð¼ÐµÑÑ‚Ð¸Ð»Ð¸ÑÑŒ Ð·Ð° " + totalDays + " Ð´Ð½.";
            case "hi" -> scheduled + "/" + selected + " à¤œà¤—à¤¹à¥‡à¤‚ à¤¶à¥‡à¤¡à¥à¤¯à¥‚à¤² à¤®à¥‡à¤‚à¥¤ "
                    + skipped + " à¤œà¤—à¤¹à¥‹à¤‚ à¤•à¥‡ à¤²à¤¿à¤ " + totalDays + " à¤¦à¤¿à¤¨ à¤®à¥‡à¤‚ à¤¸à¤®à¤¯ à¤¨à¤¹à¥€à¤‚à¥¤";
            default -> "Scheduled " + scheduled + "/" + selected + " places. "
                    + skipped + " could not fit within " + totalDays + " days.";
        };
    }

    private static String coveragePartialDays(
            String lang, int scheduled, int filledDays, int totalDays) {
        int remaining = totalDays - filledDays;
        return switch (normalize(lang)) {
            case "vi" -> "ÄÃ£ xáº¿p " + scheduled + " Ä‘á»‹a Ä‘iá»ƒm cho khoáº£ng " + filledDays
                    + " ngÃ y Ä‘áº§u. " + remaining + " ngÃ y cÃ²n láº¡i Ä‘á»ƒ báº¡n tá»± thÃªm.";
            case "ko" -> "ì²˜ìŒ " + filledDays + "ì¼ì— " + scheduled + "ê³³ì„ ë°°ì¹˜í–ˆìŠµë‹ˆë‹¤. "
                    + remaining + "ì¼ì€ ì§ì ‘ ì¶”ê°€í•˜ì„¸ìš”.";
            case "th" -> "à¸ˆà¸±à¸” " + scheduled + " à¹à¸«à¹ˆà¸‡à¹ƒà¸™à¸›à¸£à¸°à¸¡à¸²à¸“ " + filledDays + " à¸§à¸±à¸™à¹à¸£à¸ "
                    + "à¹€à¸«à¸¥à¸·à¸­ " + remaining + " à¸§à¸±à¸™à¹ƒà¸«à¹‰à¹€à¸žà¸´à¹ˆà¸¡à¹€à¸­à¸‡";
            case "ja" -> "æœ€åˆã®" + filledDays + "æ—¥ã«" + scheduled + "ä»¶ã‚’é…ç½®ã€‚"
                    + "æ®‹ã‚Š" + remaining + "æ—¥ã¯ã”è‡ªèº«ã§è¿½åŠ ã§ãã¾ã™ã€‚";
            case "zh-TW" -> "å·²ç‚ºå‰ " + filledDays + " å¤©æŽ’å…¥ " + scheduled + " å€‹åœ°é»žï¼Œ"
                    + "å°šæœ‰ " + remaining + " å¤©å¯è‡ªè¡Œå®‰æŽ’ã€‚";
            case "ru" -> scheduled + " Ð¼ÐµÑÑ‚ Ð½Ð° Ð¿ÐµÑ€Ð²Ñ‹Ðµ " + filledDays + " Ð´Ð½. "
                    + "ÐžÑÑ‚Ð°Ð»Ð¾ÑÑŒ " + remaining + " Ð´Ð½. â€” Ð´Ð¾Ð±Ð°Ð²ÑŒÑ‚Ðµ ÑÐ°Ð¼Ð¸.";
            case "hi" -> "à¤ªà¤¹à¤²à¥‡ " + filledDays + " à¤¦à¤¿à¤¨à¥‹à¤‚ à¤®à¥‡à¤‚ " + scheduled + " à¤œà¤—à¤¹à¥‡à¤‚à¥¤ "
                    + remaining + " à¤¦à¤¿à¤¨ à¤–à¤¾à¤²à¥€ â€” à¤†à¤ª à¤œà¥‹à¤¡à¤¼ à¤¸à¤•à¤¤à¥‡ à¤¹à¥ˆà¤‚à¥¤";
            default -> scheduled + " places planned for the first " + filledDays + " days. "
                    + remaining + " days left for you to fill.";
        };
    }

    private static String coverageFull(String lang, int filledDays, int scheduledCount) {
        return switch (normalize(lang)) {
            case "vi" -> "Lá»‹ch Ä‘Ã£ Ä‘Æ°á»£c táº¡o cho " + filledDays + " ngÃ y vá»›i " + scheduledCount + " Ä‘á»‹a Ä‘iá»ƒm.";
            case "ko" -> filledDays + "ì¼ ì¼ì •ì— " + scheduledCount + "ê³³ì´ í¬í•¨ë˜ì—ˆìŠµë‹ˆë‹¤.";
            case "th" -> "à¸ªà¸£à¹‰à¸²à¸‡à¸•à¸²à¸£à¸²à¸‡ " + filledDays + " à¸§à¸±à¸™ à¸žà¸£à¹‰à¸­à¸¡ " + scheduledCount + " à¸ªà¸–à¸²à¸™à¸—à¸µà¹ˆ";
            case "ja" -> filledDays + "æ—¥é–“ã®æ—…ç¨‹ã«" + scheduledCount + "ä»¶ã‚’é…ç½®ã—ã¾ã—ãŸã€‚";
            case "zh-TW" -> "å·²ç‚º " + filledDays + " å¤©å»ºç«‹è¡Œç¨‹ï¼Œå…± " + scheduledCount + " å€‹åœ°é»žã€‚";
            case "ru" -> "ÐœÐ°Ñ€ÑˆÑ€ÑƒÑ‚ Ð½Ð° " + filledDays + " Ð´Ð½. Ñ " + scheduledCount + " Ð¼ÐµÑÑ‚Ð°Ð¼Ð¸ Ð³Ð¾Ñ‚Ð¾Ð².";
            case "hi" -> filledDays + " à¤¦à¤¿à¤¨à¥‹à¤‚ à¤•à¤¾ à¤ªà¥à¤²à¤¾à¤¨ " + scheduledCount + " à¤œà¤—à¤¹à¥‹à¤‚ à¤•à¥‡ à¤¸à¤¾à¤¥ à¤¤à¥ˆà¤¯à¤¾à¤°à¥¤";
            default -> "Schedule created for " + filledDays + " days with " + scheduledCount + " places.";
        };
    }

    public static String timeHint(int hour, String lang) {
        return switch (normalize(lang)) {
            case "vi" -> hour < 12 ? "buá»•i sÃ¡ng" : (hour < 17 ? "buá»•i chiá»u" : "buá»•i tá»‘i");
            case "ko" -> hour < 12 ? "ì˜¤ì „" : (hour < 17 ? "ì˜¤í›„" : "ì €ë…");
            case "th" -> hour < 12 ? "à¸Šà¹ˆà¸§à¸‡à¹€à¸Šà¹‰à¸²" : (hour < 17 ? "à¸Šà¹ˆà¸§à¸‡à¸šà¹ˆà¸²à¸¢" : "à¸Šà¹ˆà¸§à¸‡à¹€à¸¢à¹‡à¸™");
            case "ja" -> hour < 12 ? "åˆå‰" : (hour < 17 ? "åˆå¾Œ" : "å¤œ");
            case "zh-TW" -> hour < 12 ? "ä¸Šåˆ" : (hour < 17 ? "ä¸‹åˆ" : "æ™šä¸Š");
            case "ru" -> hour < 12 ? "ÑƒÑ‚Ñ€Ð¾Ð¼" : (hour < 17 ? "Ð´Ð½Ñ‘Ð¼" : "Ð²ÐµÑ‡ÐµÑ€Ð¾Ð¼");
            case "hi" -> hour < 12 ? "à¤¸à¥à¤¬à¤¹" : (hour < 17 ? "à¤¦à¥‹à¤ªà¤¹à¤°" : "à¤¶à¤¾à¤®");
            default -> hour < 12 ? "the morning" : (hour < 17 ? "the afternoon" : "the evening");
        };
    }

    private static String normalize(String code) {
        if (code == null) {
            return "en";
        }
        String c = code.trim();
        if (c.equalsIgnoreCase("zh-TW") || c.equalsIgnoreCase("zh")) {
            return "zh-TW";
        }
        return c.toLowerCase();
    }

    private static String fallbackVisitTipEn(String group, String sourceType, String timeHint) {
        return switch (group) {
            case "FOOD_AND_DRINK" -> "Visit in " + timeHint + ", allow 1â€“2 hours for local food.";
            case "CULTURE_AND_HERITAGE" -> "Best in " + timeHint + ", plan 2â€“3 hours to explore.";
            case "NATURE_AND_OUTDOORS" -> "Go in " + timeHint + " when it's cooler; wear comfortable shoes.";
            case "SHOPPING_AND_MARKET" -> "Visit in " + timeHint + " when it's lively; bring small cash.";
            case "ACCOMMODATION" -> "Check in/out on schedule and confirm the address before arrival.";
            case "ATTRACTIONS" -> "BOOKING".equals(sourceType)
                    ? "Book ahead if needed; arrive 15 minutes early."
                    : "Keep to the scheduled slot and avoid peak hours if possible.";
            default -> "Follow the scheduled time and allow enough time to explore.";
        };
    }

    private static String fallbackVisitTipVi(String group, String sourceType, String timeHint) {
        return switch (group) {
            case "FOOD_AND_DRINK" -> "GhÃ© vÃ o " + timeHint + ", dÃ nh khoáº£ng 1â€“2 giá» thÆ°á»Ÿng thá»©c mÃ³n Ä‘á»‹a phÆ°Æ¡ng.";
            case "CULTURE_AND_HERITAGE" -> "NÃªn Ä‘áº¿n " + timeHint + ", dÃ nh 2â€“3 giá» tham quan vÃ  chá»¥p áº£nh.";
            case "NATURE_AND_OUTDOORS" -> "Äi " + timeHint + " khi thá»i tiáº¿t mÃ¡t, mang giÃ y thoáº£i mÃ¡i.";
            case "SHOPPING_AND_MARKET" -> "GhÃ© " + timeHint + " khi chá»£/cá»­a hÃ ng Ä‘Ã´ng vui, mang tiá»n máº·t nhá».";
            case "ACCOMMODATION" -> "Check-in/check-out theo giá» lá»‹ch trÃ¬nh, xÃ¡c nháº­n Ä‘á»‹a chá»‰ trÆ°á»›c khi Ä‘áº¿n.";
            case "ATTRACTIONS" -> "BOOKING".equals(sourceType)
                    ? "Äáº·t trÆ°á»›c náº¿u cáº§n, Ä‘áº¿n sá»›m 15 phÃºt so vá»›i giá» báº¯t Ä‘áº§u."
                    : "DÃ nh Ä‘á»§ thá»i gian trong khung giá» Ä‘Ã£ xáº¿p, trÃ¡nh giá» cao Ä‘iá»ƒm náº¿u cÃ³ thá»ƒ.";
            default -> "Theo khung giá» Ä‘Ã£ xáº¿p, dÃ nh Ä‘á»§ thá»i gian tham quan vÃ  di chuyá»ƒn nháº¹ nhÃ ng.";
        };
    }

    private static String fallbackVisitTipKo(String group, String sourceType, String timeHint) {
        return switch (group) {
            case "FOOD_AND_DRINK" -> timeHint + "ì— ë°©ë¬¸í•´ 1â€“2ì‹œê°„ ì—¬ìœ ë¥¼ ë‘ì„¸ìš”.";
            case "CULTURE_AND_HERITAGE" -> timeHint + "ì— ë°©ë¬¸í•´ 2â€“3ì‹œê°„ ê´€ëžŒì„ ê¶Œìž¥í•©ë‹ˆë‹¤.";
            case "NATURE_AND_OUTDOORS" -> timeHint + "ì— ê°€ë³ê²Œ ë°©ë¬¸í•˜ê³  íŽ¸í•œ ì‹ ë°œì„ ì¤€ë¹„í•˜ì„¸ìš”.";
            case "SHOPPING_AND_MARKET" -> timeHint + "ì— ë°©ë¬¸í•˜ê³  ì†Œì•¡ í˜„ê¸ˆì„ ì¤€ë¹„í•˜ì„¸ìš”.";
            case "ACCOMMODATION" -> "ì¼ì •ì— ë§žì¶° ì²´í¬ì¸/ì•„ì›ƒí•˜ê³  ì£¼ì†Œë¥¼ ë¯¸ë¦¬ í™•ì¸í•˜ì„¸ìš”.";
            case "ATTRACTIONS" -> "BOOKING".equals(sourceType)
                    ? "í•„ìš”í•˜ë©´ ì˜ˆì•½í•˜ê³  15ë¶„ ì¼ì° ë„ì°©í•˜ì„¸ìš”."
                    : "ë°°ì •ëœ ì‹œê°„ì— ë§žì¶° ì—¬ìœ  ìžˆê²Œ ì´ë™í•˜ì„¸ìš”.";
            default -> "ì¼ì • ì‹œê°„ì„ ì§€í‚¤ê³  ì¶©ë¶„ížˆ ë‘˜ëŸ¬ë³´ì„¸ìš”.";
        };
    }

    private static String fallbackVisitTipTh(String group, String sourceType, String timeHint) {
        return switch (group) {
            case "FOOD_AND_DRINK" -> "à¹„à¸›à¸Šà¹ˆà¸§à¸‡" + timeHint + " à¹€à¸œà¸·à¹ˆà¸­à¹€à¸§à¸¥à¸² 1â€“2 à¸Šà¸±à¹ˆà¸§à¹‚à¸¡à¸‡";
            case "CULTURE_AND_HERITAGE" -> "à¹à¸™à¸°à¸™à¸³à¸Šà¹ˆà¸§à¸‡" + timeHint + " à¹ƒà¸Šà¹‰à¹€à¸§à¸¥à¸² 2â€“3 à¸Šà¸±à¹ˆà¸§à¹‚à¸¡à¸‡";
            case "NATURE_AND_OUTDOORS" -> "à¹„à¸›à¸Šà¹ˆà¸§à¸‡" + timeHint + " à¸ªà¸§à¸¡à¸£à¸­à¸‡à¹€à¸—à¹‰à¸²à¸—à¸µà¹ˆà¹€à¸”à¸´à¸™à¸ªà¸šà¸²à¸¢";
            case "SHOPPING_AND_MARKET" -> "à¹„à¸›à¸Šà¹ˆà¸§à¸‡" + timeHint + " à¹€à¸•à¸£à¸µà¸¢à¸¡à¹€à¸‡à¸´à¸™à¸ªà¸”à¸¢à¹ˆà¸­à¸¢";
            case "ACCOMMODATION" -> "à¹€à¸Šà¹‡à¸à¸­à¸´à¸™/à¹€à¸Šà¹‡à¸à¹€à¸­à¸²à¸•à¹Œà¸•à¸²à¸¡à¹€à¸§à¸¥à¸²à¹à¸¥à¸°à¸¢à¸·à¸™à¸¢à¸±à¸™à¸—à¸µà¹ˆà¸­à¸¢à¸¹à¹ˆà¸à¹ˆà¸­à¸™à¹„à¸›";
            case "ATTRACTIONS" -> "BOOKING".equals(sourceType)
                    ? "à¸ˆà¸­à¸‡à¸¥à¹ˆà¸§à¸‡à¸«à¸™à¹‰à¸²à¸«à¸²à¸à¸ˆà¸³à¹€à¸›à¹‡à¸™ à¸¡à¸²à¸–à¸¶à¸‡à¸à¹ˆà¸­à¸™ 15 à¸™à¸²à¸—à¸µ"
                    : "à¸•à¸²à¸¡à¹€à¸§à¸¥à¸²à¹ƒà¸™à¸•à¸²à¸£à¸²à¸‡à¹à¸¥à¸°à¸«à¸¥à¸µà¸à¹€à¸¥à¸µà¹ˆà¸¢à¸‡à¸Šà¸±à¹ˆà¸§à¹‚à¸¡à¸‡à¸„à¸™à¹€à¸¢à¸­à¸°";
            default -> "à¸•à¸²à¸¡à¹€à¸§à¸¥à¸²à¹ƒà¸™à¸•à¸²à¸£à¸²à¸‡à¹à¸¥à¸°à¹€à¸œà¸·à¹ˆà¸­à¹€à¸§à¸¥à¸²à¹€à¸”à¸´à¸™à¸—à¸²à¸‡";
        };
    }

    private static String fallbackVisitTipJa(String group, String sourceType, String timeHint) {
        return switch (group) {
            case "FOOD_AND_DRINK" -> timeHint + "ã«è¨ªã‚Œã€1ã€œ2æ™‚é–“ã»ã©ã‚†ã£ãã‚Šæ¥½ã—ã¿ã¾ã—ã‚‡ã†ã€‚";
            case "CULTURE_AND_HERITAGE" -> timeHint + "ã®è¦‹å­¦ã«2ã€œ3æ™‚é–“ã»ã©ç¢ºä¿ã—ã¾ã—ã‚‡ã†ã€‚";
            case "NATURE_AND_OUTDOORS" -> timeHint + "ã«è¡Œãã€æ­©ãã‚„ã™ã„é´ã‚’ç”¨æ„ã—ã¾ã—ã‚‡ã†ã€‚";
            case "SHOPPING_AND_MARKET" -> timeHint + "ã«è¨ªã‚Œã€å°é¡ã®ç¾é‡‘ã‚’ç”¨æ„ã—ã¾ã—ã‚‡ã†ã€‚";
            case "ACCOMMODATION" -> "äºˆå®šé€šã‚Šã«ãƒã‚§ãƒƒã‚¯ã‚¤ãƒ³/ã‚¢ã‚¦ãƒˆã—ã€ä½æ‰€ã‚’äº‹å‰ç¢ºèªã—ã¾ã—ã‚‡ã†ã€‚";
            case "ATTRACTIONS" -> "BOOKING".equals(sourceType)
                    ? "å¿…è¦ãªã‚‰äºˆç´„ã—ã€15åˆ†æ—©ã‚ã«åˆ°ç€ã—ã¾ã—ã‚‡ã†ã€‚"
                    : "äºˆå®šæ™‚é–“ã«åˆã‚ã›ã€æ··é›‘ã‚’é¿ã‘ã‚‰ã‚Œã‚‹ã¨ã‚ˆã‚Šå¿«é©ã§ã™ã€‚";
            default -> "äºˆå®šæ™‚é–“ã«åˆã‚ã›ã¦ã‚†ã£ãã‚Šå·¡ã‚Šã¾ã—ã‚‡ã†ã€‚";
        };
    }

    private static String fallbackVisitTipZhTw(String group, String sourceType, String timeHint) {
        return switch (group) {
            case "FOOD_AND_DRINK" -> "å»ºè­°åœ¨" + timeHint + "å‰å¾€ï¼Œé ç•™ 1â€“2 å°æ™‚å“åšåœ¨åœ°ç¾Žé£Ÿã€‚";
            case "CULTURE_AND_HERITAGE" -> "å»ºè­°åœ¨" + timeHint + "åƒè§€ï¼Œé ç•™ 2â€“3 å°æ™‚ã€‚";
            case "NATURE_AND_OUTDOORS" -> "å»ºè­°åœ¨" + timeHint + "å‰å¾€ï¼Œç©¿èˆ’é©çš„éž‹å­ã€‚";
            case "SHOPPING_AND_MARKET" -> "å»ºè­°åœ¨" + timeHint + "å‰å¾€ï¼Œæº–å‚™å°‘é‡ç¾é‡‘ã€‚";
            case "ACCOMMODATION" -> "ä¾è¡Œç¨‹è¾¦ç†å…¥ä½/é€€æˆ¿ï¼Œå‡ºç™¼å‰ç¢ºèªåœ°å€ã€‚";
            case "ATTRACTIONS" -> "BOOKING".equals(sourceType)
                    ? "å¦‚éœ€è«‹å…ˆé ç´„ï¼Œä¸¦æå‰ 15 åˆ†é˜æŠµé”ã€‚"
                    : "ä¾æŽ’å®šæ™‚é–“å‰å¾€ï¼Œç›¡é‡é¿é–‹å°–å³°æ™‚æ®µã€‚";
            default -> "ä¾æŽ’å®šæ™‚é–“å®‰æŽ’ï¼Œä¿ç•™è¶³å¤ åƒè§€æ™‚é–“ã€‚";
        };
    }

    private static String fallbackVisitTipRu(String group, String sourceType, String timeHint) {
        return switch (group) {
            case "FOOD_AND_DRINK" -> "Ð›ÑƒÑ‡ÑˆÐµ " + timeHint + ", Ð·Ð°Ð»Ð¾Ð¶Ð¸Ñ‚Ðµ 1â€“2 Ñ‡Ð°ÑÐ° Ð½Ð° ÐµÐ´Ñƒ.";
            case "CULTURE_AND_HERITAGE" -> "ÐŸÐ¾ÑÐµÑ‚Ð¸Ñ‚Ðµ " + timeHint + ", Ð·Ð°Ð¿Ð»Ð°Ð½Ð¸Ñ€ÑƒÐ¹Ñ‚Ðµ 2â€“3 Ñ‡Ð°ÑÐ°.";
            case "NATURE_AND_OUTDOORS" -> "Ð˜Ð´Ð¸Ñ‚Ðµ " + timeHint + ", Ð½Ð°Ð´ÐµÐ½ÑŒÑ‚Ðµ ÑƒÐ´Ð¾Ð±Ð½ÑƒÑŽ Ð¾Ð±ÑƒÐ²ÑŒ.";
            case "SHOPPING_AND_MARKET" -> "Ð—Ð°Ð³Ð»ÑÐ½Ð¸Ñ‚Ðµ " + timeHint + ", Ð²Ð¾Ð·ÑŒÐ¼Ð¸Ñ‚Ðµ Ð½ÐµÐ¼Ð½Ð¾Ð³Ð¾ Ð½Ð°Ð»Ð¸Ñ‡Ð½Ñ‹Ñ….";
            case "ACCOMMODATION" -> "Ð—Ð°ÑÐµÐ»ÑÐ¹Ñ‚ÐµÑÑŒ/Ð²Ñ‹ÐµÐ·Ð¶Ð°Ð¹Ñ‚Ðµ Ð¿Ð¾ Ñ€Ð°ÑÐ¿Ð¸ÑÐ°Ð½Ð¸ÑŽ, Ð¿Ñ€Ð¾Ð²ÐµÑ€ÑŒÑ‚Ðµ Ð°Ð´Ñ€ÐµÑ Ð·Ð°Ñ€Ð°Ð½ÐµÐµ.";
            case "ATTRACTIONS" -> "BOOKING".equals(sourceType)
                    ? "ÐŸÑ€Ð¸ Ð½ÐµÐ¾Ð±Ñ…Ð¾Ð´Ð¸Ð¼Ð¾ÑÑ‚Ð¸ Ð·Ð°Ð±Ñ€Ð¾Ð½Ð¸Ñ€ÑƒÐ¹Ñ‚Ðµ Ð·Ð°Ñ€Ð°Ð½ÐµÐµ, Ð¿Ñ€Ð¸Ñ…Ð¾Ð´Ð¸Ñ‚Ðµ Ð·Ð° 15 Ð¼Ð¸Ð½ÑƒÑ‚."
                    : "Ð¡Ð¾Ð±Ð»ÑŽÐ´Ð°Ð¹Ñ‚Ðµ Ð²Ñ€ÐµÐ¼Ñ Ð² Ñ€Ð°ÑÐ¿Ð¸ÑÐ°Ð½Ð¸Ð¸ Ð¸ Ð¿Ð¾ Ð²Ð¾Ð·Ð¼Ð¾Ð¶Ð½Ð¾ÑÑ‚Ð¸ Ð¸Ð·Ð±ÐµÐ³Ð°Ð¹Ñ‚Ðµ Ñ‡Ð°Ñ Ð¿Ð¸Ðº.";
            default -> "Ð¡Ð»ÐµÐ´ÑƒÐ¹Ñ‚Ðµ Ñ€Ð°ÑÐ¿Ð¸ÑÐ°Ð½Ð¸ÑŽ Ð¸ Ð¾ÑÑ‚Ð°Ð²ÑŒÑ‚Ðµ Ð²Ñ€ÐµÐ¼Ñ Ð½Ð° Ð¾ÑÐ¼Ð¾Ñ‚Ñ€.";
        };
    }

    private static String fallbackVisitTipHi(String group, String sourceType, String timeHint) {
        return switch (group) {
            case "FOOD_AND_DRINK" -> timeHint + " à¤œà¤¾à¤à¤‚, à¤¸à¥à¤¥à¤¾à¤¨à¥€à¤¯ à¤–à¤¾à¤¨à¥‡ à¤•à¥‡ à¤²à¤¿à¤ 1â€“2 à¤˜à¤‚à¤Ÿà¥‡ à¤°à¤–à¥‡à¤‚à¥¤";
            case "CULTURE_AND_HERITAGE" -> timeHint + " à¤œà¤¾à¤à¤‚, à¤˜à¥‚à¤®à¤¨à¥‡ à¤•à¥‡ à¤²à¤¿à¤ 2â€“3 à¤˜à¤‚à¤Ÿà¥‡ à¤°à¤–à¥‡à¤‚à¥¤";
            case "NATURE_AND_OUTDOORS" -> timeHint + " à¤œà¤¾à¤à¤‚, à¤†à¤°à¤¾à¤®à¤¦à¤¾à¤¯à¤• à¤œà¥‚à¤¤à¥‡ à¤ªà¤¹à¤¨à¥‡à¤‚à¥¤";
            case "SHOPPING_AND_MARKET" -> timeHint + " à¤œà¤¾à¤à¤‚, à¤¥à¥‹à¤¡à¤¼à¥€ à¤¨à¤•à¤¦à¥€ à¤¸à¤¾à¤¥ à¤°à¤–à¥‡à¤‚à¥¤";
            case "ACCOMMODATION" -> "à¤¸à¤®à¤¯ à¤ªà¤° check-in/out à¤•à¤°à¥‡à¤‚, à¤ªà¤¹à¥à¤‚à¤šà¤¨à¥‡ à¤¸à¥‡ à¤ªà¤¹à¤²à¥‡ à¤ªà¤¤à¤¾ à¤ªà¥à¤·à¥à¤Ÿà¤¿ à¤•à¤°à¥‡à¤‚à¥¤";
            case "ATTRACTIONS" -> "BOOKING".equals(sourceType)
                    ? "à¤œà¤°à¥‚à¤°à¤¤ à¤¹à¥‹ à¤¤à¥‹ à¤ªà¤¹à¤²à¥‡ à¤¬à¥à¤• à¤•à¤°à¥‡à¤‚, 15 à¤®à¤¿à¤¨à¤Ÿ à¤ªà¤¹à¤²à¥‡ à¤ªà¤¹à¥à¤‚à¤šà¥‡à¤‚à¥¤"
                    : "à¤¤à¤¯ à¤¸à¤®à¤¯ à¤ªà¤° à¤œà¤¾à¤à¤‚ à¤”à¤° à¤­à¥€à¤¡à¤¼ à¤¸à¥‡ à¤¬à¤šà¥‡à¤‚à¥¤";
            default -> "à¤¤à¤¯ à¤¸à¤®à¤¯ à¤•à¤¾ à¤ªà¤¾à¤²à¤¨ à¤•à¤°à¥‡à¤‚ à¤”à¤° à¤ªà¤°à¥à¤¯à¤Ÿà¤¨ à¤•à¥‡ à¤²à¤¿à¤ à¤¸à¤®à¤¯ à¤°à¤–à¥‡à¤‚à¥¤";
        };
    }
}
