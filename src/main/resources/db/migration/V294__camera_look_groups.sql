-- Camera looks become the server's to decide: the app shows the server's filters, frames and
-- film shelves in the server's order, and falls back to its bundled set only while it has never
-- reached the server.
--
-- A film shelf (Travel, Camera colour, Film stock, Black & white) is a third kind of row in the
-- same table: it needs exactly what a look has (a stable key, names per language, an order, a
-- status) and nothing else, so its definition is the empty object. A filter names its shelf in
-- definition.group.

SET LOCAL lock_timeout = '10s';

ALTER TABLE camera_looks DROP CONSTRAINT IF EXISTS camera_looks_kind_check;
ALTER TABLE camera_looks
    ADD CONSTRAINT camera_looks_kind_check CHECK (kind IN ('FILTER', 'FRAME', 'GROUP'));
