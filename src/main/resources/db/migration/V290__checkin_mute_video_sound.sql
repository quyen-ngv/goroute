-- A video check-in with background music can keep the clip's own sound mixed in (FALSE) or
-- play only the music (TRUE). Meaningful only for a video post with music.
ALTER TABLE user_checkins ADD COLUMN IF NOT EXISTS mute_video_sound BOOLEAN NOT NULL DEFAULT FALSE;

-- Posts made before the choice existed played only their music: keep them that way.
UPDATE user_checkins SET mute_video_sound = TRUE WHERE music_track_id IS NOT NULL;
