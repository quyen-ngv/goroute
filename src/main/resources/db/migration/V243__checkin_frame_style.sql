-- The frame a check-in's photos are shown in on the Feed and on the check-in's page (epic 08):
-- an airplane window, a film strip, a postage stamp, an air-mail envelope with a postmark, or a
-- train window. The frames are drawn by the app; the server only stores which one was chosen.
--
-- Additive only: one nullable column. NULL is "no frame", which is how every existing check-in
-- reads and how the app shows photos today.
ALTER TABLE user_checkins ADD COLUMN IF NOT EXISTS frame_style VARCHAR(32);

ALTER TABLE user_checkins DROP CONSTRAINT IF EXISTS ck_user_checkins_frame_style;
ALTER TABLE user_checkins ADD CONSTRAINT ck_user_checkins_frame_style CHECK (
    frame_style IS NULL
    OR frame_style IN ('AIRPLANE_WINDOW', 'FILM_STRIP', 'STAMP', 'POSTMARK', 'TRAIN_WINDOW')
);
