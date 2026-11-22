-- What the user did with a place on their saved list (epic 06, §2.6), one row per user and
-- place, whatever category the place was saved under.
--
-- visited_at: the user marked the place as been there; null while not.
-- removed_at: the user took the place off the list. saved_places rows are deleted outright,
-- but a place found in one of the user's videos has no saved_places row, so this is what keeps
-- it off the list: the list skips a video's place whose job finished before removed_at.
CREATE TABLE IF NOT EXISTS saved_place_marks (
    user_id UUID NOT NULL,
    place_id VARCHAR(255) NOT NULL,
    visited_at TIMESTAMP,
    removed_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, place_id)
);
