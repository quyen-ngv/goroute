-- Food varieties: e.g. phở bò tái, nạm, gầu (image + name + description per variety)

ALTER TABLE foods
    ADD COLUMN IF NOT EXISTS varieties TEXT;
