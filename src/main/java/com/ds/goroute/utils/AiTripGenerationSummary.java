package com.ds.goroute.utils;

import com.ds.goroute.dto.response.AiTripCandidateResponse;
import com.ds.goroute.entity.AiTripDraft;

import java.util.List;

/** Localized generation summary shown after AI trip confirm. */
public final class AiTripGenerationSummary {

    private AiTripGenerationSummary() {
    }

    public static String build(
            AiTripDraft draft,
            int selectedCount,
            int scheduledCount,
            List<AiTripCandidateResponse> skipped,
            int filledDays) {
        int totalDays = draft.getDayCount();
        String paceLabel = AiTripLanguageSupport.paceLabel(draft.getPace());
        int capHours = paceCapHours(draft.getPace());
        String lang = AiTripLanguageSupport.currentCode();

        return switch (lang) {
            case "vi" -> buildVi(draft, selectedCount, scheduledCount, totalDays, filledDays, paceLabel, capHours, skipped);
            case "ko" -> buildKo(draft, selectedCount, scheduledCount, totalDays, filledDays, paceLabel, capHours, skipped);
            case "th" -> buildTh(draft, selectedCount, scheduledCount, totalDays, filledDays, paceLabel, capHours, skipped);
            case "ja" -> buildJa(draft, selectedCount, scheduledCount, totalDays, filledDays, paceLabel, capHours, skipped);
            case "zh-TW" -> buildZhTw(draft, selectedCount, scheduledCount, totalDays, filledDays, paceLabel, capHours, skipped);
            case "ru" -> buildRu(draft, selectedCount, scheduledCount, totalDays, filledDays, paceLabel, capHours, skipped);
            case "hi" -> buildHi(draft, selectedCount, scheduledCount, totalDays, filledDays, paceLabel, capHours, skipped);
            default -> buildEn(draft, selectedCount, scheduledCount, totalDays, filledDays, paceLabel, capHours, skipped);
        };
    }

    private static int paceCapHours(String pace) {
        if (pace == null) {
            return 9;
        }
        return switch (pace.toUpperCase()) {
            case "RELAXED" -> 7;
            case "EAGER" -> 11;
            default -> 9;
        };
    }

    private static String buildEn(
            AiTripDraft draft, int selected, int scheduled, int totalDays, int filledDays,
            String paceLabel, int capHours, List<AiTripCandidateResponse> skipped) {
        StringBuilder s = new StringBuilder();
        s.append("GoRoute AI built a schedule from ").append(selected).append(" places you chose");
        s.append(" across ").append(totalDays).append(" days in ").append(draft.getCityName()).append(".\n\n");
        s.append("How it was planned:\n");
        s.append("â€¢ Pace: ").append(paceLabel).append(" (~").append(capHours).append(" active hours/day, from ~9:00).\n");
        s.append("â€¢ Nearby stops are grouped to reduce travel time.\n");
        s.append("â€¢ Each stop includes visit time plus 30 minutes transfer.\n\n");
        s.append("Result:\n");
        s.append("â€¢ ").append(scheduled).append(" places added to the itinerary");
        if (filledDays > 0) {
            s.append(" (covering about the first ").append(filledDays).append(" days)");
        }
        s.append(".\n");
        appendRemainingDaysEn(s, filledDays, totalDays, scheduled);
        appendSkippedEn(s, skipped, scheduled, selected);
        return s.toString().trim();
    }

    private static String buildVi(
            AiTripDraft draft, int selected, int scheduled, int totalDays, int filledDays,
            String paceLabel, int capHours, List<AiTripCandidateResponse> skipped) {
        StringBuilder s = new StringBuilder();
        s.append("GoRoute AI Ä‘Ã£ táº¡o lá»‹ch tá»« ").append(selected).append(" Ä‘á»‹a Ä‘iá»ƒm báº¡n chá»n");
        s.append(" trong ").append(totalDays).append(" ngÃ y táº¡i ").append(draft.getCityName()).append(".\n\n");
        s.append("CÃ¡ch xáº¿p lá»‹ch:\n");
        s.append("â€¢ Nhá»‹p di chuyá»ƒn: ").append(paceLabel)
                .append(" (~").append(capHours).append(" giá» hoáº¡t Ä‘á»™ng/ngÃ y, báº¯t Ä‘áº§u khoáº£ng 9:00).\n");
        s.append("â€¢ Sáº¯p cÃ¡c Ä‘iá»ƒm gáº§n nhau Ä‘á»ƒ giáº£m thá»i gian di chuyá»ƒn.\n");
        s.append("â€¢ Má»—i Ä‘á»‹a Ä‘iá»ƒm cÃ³ thá»i gian tham quan Æ°á»›c tÃ­nh + 30 phÃºt chuyá»ƒn tiáº¿p.\n\n");
        s.append("Káº¿t quáº£:\n");
        s.append("â€¢ ÄÃ£ thÃªm ").append(scheduled).append(" Ä‘á»‹a Ä‘iá»ƒm vÃ o lá»‹ch");
        if (filledDays > 0) {
            s.append(" (phá»§ khoáº£ng ").append(filledDays).append(" ngÃ y Ä‘áº§u)");
        }
        s.append(".\n");
        if (filledDays < totalDays && scheduled > 0) {
            s.append("â€¢ ").append(totalDays - filledDays)
                    .append(" ngÃ y cÃ²n láº¡i chÆ°a cÃ³ hoáº¡t Ä‘á»™ng â€” báº¡n cÃ³ thá»ƒ tá»± thÃªm sau.\n");
        }
        if (!skipped.isEmpty()) {
            s.append("\nKhÃ´ng Ä‘á»§ thá»i gian Ä‘á»ƒ xáº¿p thÃªm ").append(skipped.size()).append(" Ä‘á»‹a Ä‘iá»ƒm:\n");
            for (AiTripCandidateResponse c : skipped) {
                s.append("â€¢ ").append(c.getName()).append("\n");
            }
            s.append("\nGá»£i Ã½: kÃ©o dÃ i sá»‘ ngÃ y, chá»n nhá»‹p \"DÃ y\" hÆ¡n, hoáº·c bá»›t Ä‘á»‹a Ä‘iá»ƒm khi táº¡o trip láº§n sau.");
        } else if (scheduled > 0 && scheduled == selected) {
            s.append("\nTáº¥t cáº£ Ä‘á»‹a Ä‘iá»ƒm báº¡n chá»n Ä‘Ã£ Ä‘Æ°á»£c Ä‘Æ°a vÃ o lá»‹ch.");
        }
        return s.toString().trim();
    }

    private static String buildKo(
            AiTripDraft draft, int selected, int scheduled, int totalDays, int filledDays,
            String paceLabel, int capHours, List<AiTripCandidateResponse> skipped) {
        StringBuilder s = new StringBuilder();
        s.append("GoRoute AIê°€ ì„ íƒí•œ ").append(selected).append("ê³³ì„ ë°”íƒ•ìœ¼ë¡œ ");
        s.append(draft.getCityName()).append(" ").append(totalDays).append("ì¼ ì¼ì •ì„ ë§Œë“¤ì—ˆìŠµë‹ˆë‹¤.\n\n");
        s.append("ì¼ì • êµ¬ì„±:\n");
        s.append("â€¢ íŽ˜ì´ìŠ¤: ").append(paceLabel).append(" (í•˜ë£¨ ì•½ ").append(capHours).append("ì‹œê°„, 9ì‹œ ì „í›„ ì‹œìž‘).\n");
        s.append("â€¢ ê°€ê¹Œìš´ ìž¥ì†Œë¼ë¦¬ ë¬¶ì–´ ì´ë™ ì‹œê°„ì„ ì¤„ì˜€ìŠµë‹ˆë‹¤.\n");
        s.append("â€¢ ê° ìž¥ì†Œì— ë°©ë¬¸ ì‹œê°„ + ì´ë™ 30ë¶„ì„ ë°˜ì˜í–ˆìŠµë‹ˆë‹¤.\n\n");
        s.append("ê²°ê³¼:\n");
        s.append("â€¢ ").append(scheduled).append("ê³³ì´ ì¼ì •ì— ì¶”ê°€ë¨");
        if (filledDays > 0) {
            s.append(" (ì²˜ìŒ ").append(filledDays).append("ì¼ ì •ë„)");
        }
        s.append(".\n");
        appendRemainingDaysKo(s, filledDays, totalDays, scheduled);
        appendSkippedKo(s, skipped, scheduled, selected);
        return s.toString().trim();
    }

    private static String buildTh(
            AiTripDraft draft, int selected, int scheduled, int totalDays, int filledDays,
            String paceLabel, int capHours, List<AiTripCandidateResponse> skipped) {
        StringBuilder s = new StringBuilder();
        s.append("GoRoute AI à¸ªà¸£à¹‰à¸²à¸‡à¸—à¸£à¸´à¸›à¸ˆà¸²à¸ ").append(selected).append(" à¸ªà¸–à¸²à¸™à¸—à¸µà¹ˆà¸—à¸µà¹ˆà¸„à¸¸à¸“à¹€à¸¥à¸·à¸­à¸ ");
        s.append("à¹ƒà¸™ ").append(totalDays).append(" à¸§à¸±à¸™à¸—à¸µà¹ˆ ").append(draft.getCityName()).append("\n\n");
        s.append("à¸§à¸´à¸˜à¸µà¸ˆà¸±à¸”à¸—à¸£à¸´à¸›:\n");
        s.append("â€¢ à¸ˆà¸±à¸‡à¸«à¸§à¸°: ").append(paceLabel).append(" (~").append(capHours).append(" à¸Šà¸¡./à¸§à¸±à¸™ à¹€à¸£à¸´à¹ˆà¸¡ ~9:00)\n");
        s.append("â€¢ à¸ˆà¸±à¸”à¸ˆà¸¸à¸”à¹ƒà¸à¸¥à¹‰à¸à¸±à¸™à¹€à¸žà¸·à¹ˆà¸­à¸¥à¸”à¹€à¸§à¸¥à¸²à¹€à¸”à¸´à¸™à¸—à¸²à¸‡\n");
        s.append("â€¢ à¹à¸•à¹ˆà¸¥à¸°à¸ˆà¸¸à¸”à¸¡à¸µà¹€à¸§à¸¥à¸²à¹€à¸—à¸µà¹ˆà¸¢à¸§ + à¹€à¸§à¸¥à¸²à¹€à¸”à¸´à¸™à¸—à¸²à¸‡ 30 à¸™à¸²à¸—à¸µ\n\n");
        s.append("à¸œà¸¥à¸¥à¸±à¸žà¸˜à¹Œ:\n");
        s.append("â€¢ à¹€à¸žà¸´à¹ˆà¸¡ ").append(scheduled).append(" à¸ªà¸–à¸²à¸™à¸—à¸µà¹ˆà¹ƒà¸™à¸•à¸²à¸£à¸²à¸‡");
        if (filledDays > 0) {
            s.append(" (à¸„à¸£à¸­à¸šà¸„à¸¥à¸¸à¸¡à¸›à¸£à¸°à¸¡à¸²à¸“ ").append(filledDays).append(" à¸§à¸±à¸™à¹à¸£à¸)");
        }
        s.append("\n");
        appendRemainingDaysTh(s, filledDays, totalDays, scheduled);
        appendSkippedTh(s, skipped, scheduled, selected);
        return s.toString().trim();
    }

    private static String buildJa(
            AiTripDraft draft, int selected, int scheduled, int totalDays, int filledDays,
            String paceLabel, int capHours, List<AiTripCandidateResponse> skipped) {
        StringBuilder s = new StringBuilder();
        s.append("GoRoute AIãŒé¸æŠžã—ãŸ").append(selected).append("ä»¶ã‹ã‚‰ã€");
        s.append(draft.getCityName()).append("ã®").append(totalDays).append("æ—¥é–“ã®æ—…ç¨‹ã‚’ä½œæˆã—ã¾ã—ãŸã€‚\n\n");
        s.append("çµ„ã¿ç«‹ã¦æ–¹:\n");
        s.append("â€¢ ãƒšãƒ¼ã‚¹: ").append(paceLabel).append("ï¼ˆ1æ—¥ç´„").append(capHours).append("æ™‚é–“ã€9æ™‚é ƒé–‹å§‹ï¼‰\n");
        s.append("â€¢ è¿‘ã„ã‚¹ãƒãƒƒãƒˆã‚’ã¾ã¨ã‚ã¦ç§»å‹•ã‚’æŠ‘ãˆã¾ã—ãŸ\n");
        s.append("â€¢ å„ã‚¹ãƒãƒƒãƒˆã«æ»žåœ¨æ™‚é–“ï¼‹ç§»å‹•30åˆ†ã‚’åæ˜ \n\n");
        s.append("çµæžœ:\n");
        s.append("â€¢ ").append(scheduled).append("ä»¶ã‚’æ—…ç¨‹ã«è¿½åŠ ");
        if (filledDays > 0) {
            s.append("ï¼ˆæœ€åˆã®").append(filledDays).append("æ—¥ç¨‹åº¦ï¼‰");
        }
        s.append("\n");
        appendRemainingDaysJa(s, filledDays, totalDays, scheduled);
        appendSkippedJa(s, skipped, scheduled, selected);
        return s.toString().trim();
    }

    private static String buildZhTw(
            AiTripDraft draft, int selected, int scheduled, int totalDays, int filledDays,
            String paceLabel, int capHours, List<AiTripCandidateResponse> skipped) {
        StringBuilder s = new StringBuilder();
        s.append("GoRoute AI å·²ä¾ä½ é¸æ“‡çš„ ").append(selected).append(" å€‹åœ°é»žï¼Œ");
        s.append("ç‚º ").append(draft.getCityName()).append(" çš„ ").append(totalDays).append(" å¤©è¡Œç¨‹æŽ’ç¨‹ã€‚\n\n");
        s.append("æŽ’ç¨‹æ–¹å¼:\n");
        s.append("â€¢ ç¯€å¥: ").append(paceLabel).append("ï¼ˆæ¯æ—¥ç´„ ").append(capHours).append(" å°æ™‚ï¼Œç´„ 9:00 é–‹å§‹ï¼‰\n");
        s.append("â€¢ å°‡ç›¸è¿‘åœ°é»žé›†ä¸­ä»¥æ¸›å°‘ç§»å‹•\n");
        s.append("â€¢ æ¯å€‹åœ°é»žå«åœç•™æ™‚é–“ + 30 åˆ†é˜è½‰å ´\n\n");
        s.append("çµæžœ:\n");
        s.append("â€¢ å·²åŠ å…¥ ").append(scheduled).append(" å€‹åœ°é»ž");
        if (filledDays > 0) {
            s.append("ï¼ˆç´„å‰ ").append(filledDays).append(" å¤©ï¼‰");
        }
        s.append("\n");
        appendRemainingDaysZhTw(s, filledDays, totalDays, scheduled);
        appendSkippedZhTw(s, skipped, scheduled, selected);
        return s.toString().trim();
    }

    private static String buildRu(
            AiTripDraft draft, int selected, int scheduled, int totalDays, int filledDays,
            String paceLabel, int capHours, List<AiTripCandidateResponse> skipped) {
        StringBuilder s = new StringBuilder();
        s.append("GoRoute AI ÑÐ¾ÑÑ‚Ð°Ð²Ð¸Ð» Ð¼Ð°Ñ€ÑˆÑ€ÑƒÑ‚ Ð¸Ð· ").append(selected).append(" Ð²Ñ‹Ð±Ñ€Ð°Ð½Ð½Ñ‹Ñ… Ð¼ÐµÑÑ‚ ");
        s.append("Ð½Ð° ").append(totalDays).append(" Ð´Ð½. Ð² ").append(draft.getCityName()).append(".\n\n");
        s.append("ÐšÐ°Ðº Ð¿Ð»Ð°Ð½Ð¸Ñ€Ð¾Ð²Ð°Ð»Ð¸:\n");
        s.append("â€¢ Ð¢ÐµÐ¼Ð¿: ").append(paceLabel).append(" (~").append(capHours).append(" Ñ‡/Ð´ÐµÐ½ÑŒ, ÑÑ‚Ð°Ñ€Ñ‚ ~9:00).\n");
        s.append("â€¢ Ð‘Ð»Ð¸Ð·ÐºÐ¸Ðµ Ñ‚Ð¾Ñ‡ÐºÐ¸ ÑÐ³Ñ€ÑƒÐ¿Ð¿Ð¸Ñ€Ð¾Ð²Ð°Ð½Ñ‹ Ð´Ð»Ñ ÑÐºÐ¾Ð½Ð¾Ð¼Ð¸Ð¸ Ð²Ñ€ÐµÐ¼ÐµÐ½Ð¸.\n");
        s.append("â€¢ Ð£ ÐºÐ°Ð¶Ð´Ð¾Ð¹ Ñ‚Ð¾Ñ‡ÐºÐ¸ Ð²Ñ€ÐµÐ¼Ñ Ð²Ð¸Ð·Ð¸Ñ‚Ð° + 30 Ð¼Ð¸Ð½ Ð½Ð° Ð¿ÐµÑ€ÐµÐµÐ·Ð´.\n\n");
        s.append("Ð˜Ñ‚Ð¾Ð³:\n");
        s.append("â€¢ Ð’ Ð¼Ð°Ñ€ÑˆÑ€ÑƒÑ‚ Ð´Ð¾Ð±Ð°Ð²Ð»ÐµÐ½Ð¾ ").append(scheduled).append(" Ð¼ÐµÑÑ‚");
        if (filledDays > 0) {
            s.append(" (Ð¿Ñ€Ð¸Ð¼ÐµÑ€Ð½Ð¾ Ð¿ÐµÑ€Ð²Ñ‹Ðµ ").append(filledDays).append(" Ð´Ð½.)");
        }
        s.append(".\n");
        appendRemainingDaysRu(s, filledDays, totalDays, scheduled);
        appendSkippedRu(s, skipped, scheduled, selected);
        return s.toString().trim();
    }

    private static String buildHi(
            AiTripDraft draft, int selected, int scheduled, int totalDays, int filledDays,
            String paceLabel, int capHours, List<AiTripCandidateResponse> skipped) {
        StringBuilder s = new StringBuilder();
        s.append("GoRoute AI à¤¨à¥‡ à¤†à¤ªà¤•à¥€ ").append(selected).append(" à¤œà¤—à¤¹à¥‹à¤‚ à¤¸à¥‡ ");
        s.append(draft.getCityName()).append(" à¤®à¥‡à¤‚ ").append(totalDays).append(" à¤¦à¤¿à¤¨à¥‹à¤‚ à¤•à¤¾ à¤ªà¥à¤²à¤¾à¤¨ à¤¬à¤¨à¤¾à¤¯à¤¾à¥¤\n\n");
        s.append("à¤¯à¥‹à¤œà¤¨à¤¾ à¤•à¥ˆà¤¸à¥‡ à¤¬à¤¨à¥€:\n");
        s.append("â€¢ à¤—à¤¤à¤¿: ").append(paceLabel).append(" (~").append(capHours).append(" à¤˜à¤‚à¤Ÿà¥‡/à¤¦à¤¿à¤¨, ~9:00 à¤¸à¥‡).\n");
        s.append("â€¢ à¤ªà¤¾à¤¸ à¤•à¥€ à¤œà¤—à¤¹à¥‡à¤‚ à¤à¤• à¤¸à¤¾à¤¥ à¤°à¤–à¥€ à¤—à¤ˆà¤‚à¥¤\n");
        s.append("â€¢ à¤¹à¤° à¤¸à¥à¤Ÿà¥‰à¤ª à¤®à¥‡à¤‚ à¤¸à¤®à¤¯ + 30 à¤®à¤¿à¤¨à¤Ÿ à¤Ÿà¥à¤°à¤¾à¤‚à¤œà¤¼à¤¿à¤Ÿà¥¤\n\n");
        s.append("à¤ªà¤°à¤¿à¤£à¤¾à¤®:\n");
        s.append("â€¢ ").append(scheduled).append(" à¤œà¤—à¤¹à¥‡à¤‚ à¤¶à¥‡à¤¡à¥à¤¯à¥‚à¤² à¤®à¥‡à¤‚ à¤œà¥‹à¤¡à¤¼à¥€ à¤—à¤ˆà¤‚");
        if (filledDays > 0) {
            s.append(" (à¤²à¤—à¤­à¤— à¤ªà¤¹à¤²à¥‡ ").append(filledDays).append(" à¤¦à¤¿à¤¨)");
        }
        s.append(".\n");
        appendRemainingDaysHi(s, filledDays, totalDays, scheduled);
        appendSkippedHi(s, skipped, scheduled, selected);
        return s.toString().trim();
    }

    private static void appendRemainingDaysEn(StringBuilder s, int filledDays, int totalDays, int scheduled) {
        if (filledDays < totalDays && scheduled > 0) {
            s.append("â€¢ ").append(totalDays - filledDays)
                    .append(" days still empty â€” you can add activities later.\n");
        }
    }

    private static void appendSkippedEn(
            StringBuilder s, List<AiTripCandidateResponse> skipped, int scheduled, int selected) {
        if (!skipped.isEmpty()) {
            s.append("\nNot enough time for ").append(skipped.size()).append(" places:\n");
            for (AiTripCandidateResponse c : skipped) {
                s.append("â€¢ ").append(c.getName()).append("\n");
            }
            s.append("\nTip: add more days, choose a busier pace, or select fewer places next time.");
        } else if (scheduled > 0 && scheduled == selected) {
            s.append("\nAll selected places were scheduled.");
        }
    }

    private static void appendRemainingDaysKo(StringBuilder s, int filledDays, int totalDays, int scheduled) {
        if (filledDays < totalDays && scheduled > 0) {
            s.append("â€¢ ë‚¨ì€ ").append(totalDays - filledDays).append("ì¼ì€ ë¹„ì–´ ìžˆìŠµë‹ˆë‹¤.\n");
        }
    }

    private static void appendSkippedKo(
            StringBuilder s, List<AiTripCandidateResponse> skipped, int scheduled, int selected) {
        if (!skipped.isEmpty()) {
            s.append("\nì‹œê°„ ë¶€ì¡±ìœ¼ë¡œ ").append(skipped.size()).append("ê³³ ì œì™¸:\n");
            for (AiTripCandidateResponse c : skipped) {
                s.append("â€¢ ").append(c.getName()).append("\n");
            }
        } else if (scheduled == selected && scheduled > 0) {
            s.append("\nì„ íƒí•œ ëª¨ë“  ìž¥ì†Œê°€ ì¼ì •ì— í¬í•¨ë˜ì—ˆìŠµë‹ˆë‹¤.");
        }
    }

    private static void appendRemainingDaysTh(StringBuilder s, int filledDays, int totalDays, int scheduled) {
        if (filledDays < totalDays && scheduled > 0) {
            s.append("â€¢ à¹€à¸«à¸¥à¸·à¸­ ").append(totalDays - filledDays).append(" à¸§à¸±à¸™à¸—à¸µà¹ˆà¸¢à¸±à¸‡à¸§à¹ˆà¸²à¸‡\n");
        }
    }

    private static void appendSkippedTh(
            StringBuilder s, List<AiTripCandidateResponse> skipped, int scheduled, int selected) {
        if (!skipped.isEmpty()) {
            s.append("\nà¹„à¸¡à¹ˆà¸¡à¸µà¹€à¸§à¸¥à¸²à¸ªà¸³à¸«à¸£à¸±à¸š ").append(skipped.size()).append(" à¹à¸«à¹ˆà¸‡:\n");
            for (AiTripCandidateResponse c : skipped) {
                s.append("â€¢ ").append(c.getName()).append("\n");
            }
        } else if (scheduled == selected && scheduled > 0) {
            s.append("\nà¸ˆà¸±à¸”à¸„à¸£à¸šà¸—à¸¸à¸à¸ªà¸–à¸²à¸™à¸—à¸µà¹ˆà¸—à¸µà¹ˆà¹€à¸¥à¸·à¸­à¸à¹à¸¥à¹‰à¸§");
        }
    }

    private static void appendRemainingDaysJa(StringBuilder s, int filledDays, int totalDays, int scheduled) {
        if (filledDays < totalDays && scheduled > 0) {
            s.append("â€¢ æ®‹ã‚Š").append(totalDays - filledDays).append("æ—¥ã¯ç©ºãã§ã™\n");
        }
    }

    private static void appendSkippedJa(
            StringBuilder s, List<AiTripCandidateResponse> skipped, int scheduled, int selected) {
        if (!skipped.isEmpty()) {
            s.append("\næ™‚é–“ã®éƒ½åˆã§").append(skipped.size()).append("ä»¶ã¯æœªæŽ¡ç”¨:\n");
            for (AiTripCandidateResponse c : skipped) {
                s.append("â€¢ ").append(c.getName()).append("\n");
            }
        } else if (scheduled == selected && scheduled > 0) {
            s.append("\né¸æŠžã—ãŸã‚¹ãƒãƒƒãƒˆã¯ã™ã¹ã¦æ—…ç¨‹ã«å…¥ã‚Šã¾ã—ãŸã€‚");
        }
    }

    private static void appendRemainingDaysZhTw(StringBuilder s, int filledDays, int totalDays, int scheduled) {
        if (filledDays < totalDays && scheduled > 0) {
            s.append("â€¢ å°šæœ‰ ").append(totalDays - filledDays).append(" å¤©å¯å†è‡ªè¡Œå®‰æŽ’\n");
        }
    }

    private static void appendSkippedZhTw(
            StringBuilder s, List<AiTripCandidateResponse> skipped, int scheduled, int selected) {
        if (!skipped.isEmpty()) {
            s.append("\næ™‚é–“ä¸è¶³ï¼Œæœªèƒ½æŽ’å…¥ ").append(skipped.size()).append(" å€‹åœ°é»ž:\n");
            for (AiTripCandidateResponse c : skipped) {
                s.append("â€¢ ").append(c.getName()).append("\n");
            }
        } else if (scheduled == selected && scheduled > 0) {
            s.append("\nå·²å°‡æ‰€æœ‰é¸æ“‡çš„åœ°é»žæŽ’å…¥è¡Œç¨‹ã€‚");
        }
    }

    private static void appendRemainingDaysRu(StringBuilder s, int filledDays, int totalDays, int scheduled) {
        if (filledDays < totalDays && scheduled > 0) {
            s.append("â€¢ ").append(totalDays - filledDays).append(" Ð´Ð½. Ð¿Ð¾ÐºÐ° Ð±ÐµÐ· Ð°ÐºÑ‚Ð¸Ð²Ð½Ð¾ÑÑ‚ÐµÐ¹.\n");
        }
    }

    private static void appendSkippedRu(
            StringBuilder s, List<AiTripCandidateResponse> skipped, int scheduled, int selected) {
        if (!skipped.isEmpty()) {
            s.append("\nÐÐµ Ñ…Ð²Ð°Ñ‚Ð¸Ð»Ð¾ Ð²Ñ€ÐµÐ¼ÐµÐ½Ð¸ Ð´Ð»Ñ ").append(skipped.size()).append(" Ð¼ÐµÑÑ‚:\n");
            for (AiTripCandidateResponse c : skipped) {
                s.append("â€¢ ").append(c.getName()).append("\n");
            }
        } else if (scheduled == selected && scheduled > 0) {
            s.append("\nÐ’ÑÐµ Ð²Ñ‹Ð±Ñ€Ð°Ð½Ð½Ñ‹Ðµ Ð¼ÐµÑÑ‚Ð° Ð´Ð¾Ð±Ð°Ð²Ð»ÐµÐ½Ñ‹ Ð² Ð¼Ð°Ñ€ÑˆÑ€ÑƒÑ‚.");
        }
    }

    private static void appendRemainingDaysHi(StringBuilder s, int filledDays, int totalDays, int scheduled) {
        if (filledDays < totalDays && scheduled > 0) {
            s.append("â€¢ ").append(totalDays - filledDays).append(" à¤¦à¤¿à¤¨ à¤…à¤­à¥€ à¤–à¤¾à¤²à¥€ à¤¹à¥ˆà¤‚à¥¤\n");
        }
    }

    private static void appendSkippedHi(
            StringBuilder s, List<AiTripCandidateResponse> skipped, int scheduled, int selected) {
        if (!skipped.isEmpty()) {
            s.append("\n").append(skipped.size()).append(" à¤¸à¥à¤¥à¤¾à¤¨ à¤¸à¤®à¤¯ à¤•à¥€ à¤•à¤®à¥€ à¤¸à¥‡ à¤¬à¤¾à¤¹à¤°:\n");
            for (AiTripCandidateResponse c : skipped) {
                s.append("â€¢ ").append(c.getName()).append("\n");
            }
        } else if (scheduled == selected && scheduled > 0) {
            s.append("\nà¤¸à¤­à¥€ à¤šà¥à¤¨à¥€ à¤—à¤ˆ à¤œà¤—à¤¹à¥‡à¤‚ à¤¶à¥‡à¤¡à¥à¤¯à¥‚à¤² à¤®à¥‡à¤‚ à¤¹à¥ˆà¤‚à¥¤");
        }
    }
}
