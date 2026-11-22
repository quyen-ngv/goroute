package com.ds.goroute.type;

/**
 * Which compression settings an image gets. FREE only for an image known to belong to a user on
 * the free plan; everything else (PRO users, staff, partners, system content, unknown owner)
 * gets PRO, so a lookup that fails never costs a paying user quality.
 */
public enum ImageCompressionTier {
    FREE,
    PRO
}
