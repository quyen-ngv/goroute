package com.ds.goroute.dto.response;

/**
 * A server-rendered public web page.
 *
 * @param found false when the thing the link points at does not exist or is not public; the
 *              page still renders (it says so), and the controller answers 404
 * @param html  the whole document
 */
public record PublicPage(boolean found, String html) {
}
