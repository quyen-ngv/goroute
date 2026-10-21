package com.ds.goroute.service;

import com.ds.goroute.dto.response.PublicPage;

import java.util.UUID;

/**
 * The public web pages a shared link opens, rendered with the link-preview tags
 * (Open Graph, canonical URL) that Zalo, Messenger, Facebook and search engines read
 * without running the page's script.
 */
public interface PublicPageService {

    /**
     * The profile page behind {@code /u/{username}}.
     *
     * @param vietnamese the reader's browser prefers Vietnamese (or sent no preference, which is
     *                   what link-preview crawlers do); otherwise the page is English
     */
    PublicPage profilePage(String username, boolean vietnamese);

    /**
     * The Ondetour trip page. A public trip gets its own title, description and cover in the
     * preview tags; anything else, and the trip list ({@code tripId} null), keeps the page's
     * generic tags so a private trip's name never reaches a crawler.
     */
    PublicPage tripPage(UUID tripId);
}
