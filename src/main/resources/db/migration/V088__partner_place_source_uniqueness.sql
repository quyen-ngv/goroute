CREATE UNIQUE INDEX IF NOT EXISTS uq_place_sources_partner_owner
    ON place_sources(place_id, source_type, source_owner_id)
    WHERE source_owner_id IS NOT NULL;
