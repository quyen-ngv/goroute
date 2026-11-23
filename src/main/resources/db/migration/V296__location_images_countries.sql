-- Destinations go worldwide: a country is a location image too, next to the travel
-- regions inside it, so "United States" can be a trip's destination before the
-- traveller has picked states, and a search box lists countries and regions together.
--
--   kind          COUNTRY | REGION (every row so far is a Vietnamese REGION)
--   country_code  ISO 3166-1 alpha-2 of the country the row is, or is in
--   parent_id     a REGION's country row
--   currency_code the country's main currency (ISO 4217), for a trip's default
--
-- A COUNTRY row never takes part in "which tourist area is this point in": both
-- resolver functions below now read REGION rows only, so places, trips and hotels
-- keep being assigned to travel regions and never to a whole country.
--
-- Country names are stored in English; the API localizes them from the code. A
-- country's image is its flag (flagcdn.com, public-domain flags).

ALTER TABLE location_images
    ADD COLUMN kind VARCHAR(16) NOT NULL DEFAULT 'REGION',
    ADD COLUMN country_code VARCHAR(2) NOT NULL DEFAULT 'VN',
    ADD COLUMN parent_id UUID REFERENCES location_images (id) ON DELETE SET NULL,
    ADD COLUMN currency_code VARCHAR(3);

ALTER TABLE location_images
    ADD CONSTRAINT chk_location_images_kind CHECK (kind IN ('COUNTRY', 'REGION'));

CREATE UNIQUE INDEX uq_location_images_country
    ON location_images (country_code) WHERE kind = 'COUNTRY';
CREATE INDEX idx_location_images_country_kind
    ON location_images (country_code, kind);
CREATE INDEX idx_location_images_parent
    ON location_images (parent_id) WHERE parent_id IS NOT NULL;

INSERT INTO location_images
    (id, kind, country_code, full_address, normalized_address, latitude, longitude,
     coverage_radius_km, currency_code, image_url, avatar_url, priority, created_at, updated_at)
SELECT gen_random_uuid(), 'COUNTRY', c.code, c.name, c.normalized, c.lat, c.lng,
       c.radius, c.currency, c.image_url, c.avatar_url, 0, NOW(), NOW()
FROM (VALUES
    ('AD', 'Andorra', 'andorra', 42.50000, 1.50000, 12, 'EUR', 'https://flagcdn.com/w640/ad.png', 'https://flagcdn.com/w160/ad.png'),
    ('AE', 'United Arab Emirates', 'united arab emirates', 24.00000, 54.00000, 163, 'AED', 'https://flagcdn.com/w640/ae.png', 'https://flagcdn.com/w160/ae.png'),
    ('AF', 'Afghanistan', 'afghanistan', 33.00000, 65.00000, 456, 'AFN', 'https://flagcdn.com/w640/af.png', 'https://flagcdn.com/w160/af.png'),
    ('AG', 'Antigua and Barbuda', 'antigua and barbuda', 17.05000, -61.80000, 12, 'XCD', 'https://flagcdn.com/w640/ag.png', 'https://flagcdn.com/w160/ag.png'),
    ('AI', 'Anguilla', 'anguilla', 18.25000, -63.16667, 5, 'XCD', 'https://flagcdn.com/w640/ai.png', 'https://flagcdn.com/w160/ai.png'),
    ('AL', 'Albania', 'albania', 41.00000, 20.00000, 96, 'ALL', 'https://flagcdn.com/w640/al.png', 'https://flagcdn.com/w160/al.png'),
    ('AM', 'Armenia', 'armenia', 40.00000, 45.00000, 97, 'AMD', 'https://flagcdn.com/w640/am.png', 'https://flagcdn.com/w160/am.png'),
    ('AO', 'Angola', 'angola', -12.50000, 18.50000, 500, 'AOA', 'https://flagcdn.com/w640/ao.png', 'https://flagcdn.com/w160/ao.png'),
    ('AR', 'Argentina', 'argentina', -34.00000, -64.00000, 500, 'ARS', 'https://flagcdn.com/w640/ar.png', 'https://flagcdn.com/w160/ar.png'),
    ('AS', 'American Samoa', 'american samoa', -14.33333, -170.00000, 8, 'USD', 'https://flagcdn.com/w640/as.png', 'https://flagcdn.com/w160/as.png'),
    ('AT', 'Austria', 'austria', 47.33333, 13.33333, 163, 'EUR', 'https://flagcdn.com/w640/at.png', 'https://flagcdn.com/w160/at.png'),
    ('AU', 'Australia', 'australia', -27.00000, 133.00000, 500, 'AUD', 'https://flagcdn.com/w640/au.png', 'https://flagcdn.com/w160/au.png'),
    ('AW', 'Aruba', 'aruba', 12.50000, -69.96667, 8, 'AWG', 'https://flagcdn.com/w640/aw.png', 'https://flagcdn.com/w160/aw.png'),
    ('AX', 'Åland Islands', 'aland islands', 60.11667, 19.90000, 22, 'EUR', 'https://flagcdn.com/w640/ax.png', 'https://flagcdn.com/w160/ax.png'),
    ('AZ', 'Azerbaijan', 'azerbaijan', 40.50000, 47.50000, 166, 'AZN', 'https://flagcdn.com/w640/az.png', 'https://flagcdn.com/w160/az.png'),
    ('BA', 'Bosnia and Herzegovina', 'bosnia and herzegovina', 44.00000, 18.00000, 128, 'BAM', 'https://flagcdn.com/w640/ba.png', 'https://flagcdn.com/w160/ba.png'),
    ('BB', 'Barbados', 'barbados', 13.16667, -59.53333, 12, 'BBD', 'https://flagcdn.com/w640/bb.png', 'https://flagcdn.com/w160/bb.png'),
    ('BD', 'Bangladesh', 'bangladesh', 24.00000, 90.00000, 217, 'BDT', 'https://flagcdn.com/w640/bd.png', 'https://flagcdn.com/w160/bd.png'),
    ('BE', 'Belgium', 'belgium', 50.83333, 4.00000, 99, 'EUR', 'https://flagcdn.com/w640/be.png', 'https://flagcdn.com/w160/be.png'),
    ('BF', 'Burkina Faso', 'burkina faso', 13.00000, -2.00000, 295, 'XOF', 'https://flagcdn.com/w640/bf.png', 'https://flagcdn.com/w160/bf.png'),
    ('BG', 'Bulgaria', 'bulgaria', 43.00000, 25.00000, 188, 'EUR', 'https://flagcdn.com/w640/bg.png', 'https://flagcdn.com/w160/bg.png'),
    ('BH', 'Bahrain', 'bahrain', 26.00000, 50.55000, 16, 'BHD', 'https://flagcdn.com/w640/bh.png', 'https://flagcdn.com/w160/bh.png'),
    ('BI', 'Burundi', 'burundi', -3.50000, 30.00000, 94, 'BIF', 'https://flagcdn.com/w640/bi.png', 'https://flagcdn.com/w160/bi.png'),
    ('BJ', 'Benin', 'benin', 9.50000, 2.25000, 189, 'XOF', 'https://flagcdn.com/w640/bj.png', 'https://flagcdn.com/w160/bj.png'),
    ('BL', 'Saint Barthélemy', 'saint barthelemy', 18.50000, -63.41667, 5, 'EUR', 'https://flagcdn.com/w640/bl.png', 'https://flagcdn.com/w160/bl.png'),
    ('BM', 'Bermuda', 'bermuda', 32.33333, -64.75000, 5, 'BMD', 'https://flagcdn.com/w640/bm.png', 'https://flagcdn.com/w160/bm.png'),
    ('BN', 'Brunei', 'brunei', 4.50000, 114.66667, 43, 'BND', 'https://flagcdn.com/w640/bn.png', 'https://flagcdn.com/w160/bn.png'),
    ('BO', 'Bolivia', 'bolivia', -17.00000, -65.00000, 500, 'BOB', 'https://flagcdn.com/w640/bo.png', 'https://flagcdn.com/w160/bo.png'),
    ('BQ', 'Caribbean Netherlands', 'caribbean netherlands', 12.18000, -68.25000, 10, 'USD', 'https://flagcdn.com/w640/bq.png', 'https://flagcdn.com/w160/bq.png'),
    ('BR', 'Brazil', 'brazil', -10.00000, -55.00000, 500, 'BRL', 'https://flagcdn.com/w640/br.png', 'https://flagcdn.com/w160/br.png'),
    ('BS', 'Bahamas', 'bahamas', 24.25000, -76.00000, 67, 'BSD', 'https://flagcdn.com/w640/bs.png', 'https://flagcdn.com/w160/bs.png'),
    ('BT', 'Bhutan', 'bhutan', 27.50000, 90.50000, 111, 'BTN', 'https://flagcdn.com/w640/bt.png', 'https://flagcdn.com/w160/bt.png'),
    ('BW', 'Botswana', 'botswana', -22.00000, 24.00000, 430, 'BWP', 'https://flagcdn.com/w640/bw.png', 'https://flagcdn.com/w160/bw.png'),
    ('BY', 'Belarus', 'belarus', 53.00000, 28.00000, 257, 'BYN', 'https://flagcdn.com/w640/by.png', 'https://flagcdn.com/w160/by.png'),
    ('BZ', 'Belize', 'belize', 17.25000, -88.75000, 86, 'BZD', 'https://flagcdn.com/w640/bz.png', 'https://flagcdn.com/w160/bz.png'),
    ('CA', 'Canada', 'canada', 60.00000, -95.00000, 500, 'CAD', 'https://flagcdn.com/w640/ca.png', 'https://flagcdn.com/w160/ca.png'),
    ('CC', 'Cocos (Keeling) Islands', 'cocos (keeling) islands', -12.50000, 96.83333, 5, 'AUD', 'https://flagcdn.com/w640/cc.png', 'https://flagcdn.com/w160/cc.png'),
    ('CD', 'DR Congo', 'dr congo', 0.00000, 25.00000, 500, 'CDF', 'https://flagcdn.com/w640/cd.png', 'https://flagcdn.com/w160/cd.png'),
    ('CF', 'Central African Republic', 'central african republic', 7.00000, 21.00000, 445, 'XAF', 'https://flagcdn.com/w640/cf.png', 'https://flagcdn.com/w160/cf.png'),
    ('CG', 'Congo', 'congo', -1.00000, 15.00000, 330, 'XAF', 'https://flagcdn.com/w640/cg.png', 'https://flagcdn.com/w160/cg.png'),
    ('CH', 'Switzerland', 'switzerland', 47.00000, 8.00000, 115, 'CHF', 'https://flagcdn.com/w640/ch.png', 'https://flagcdn.com/w160/ch.png'),
    ('CI', 'Ivory Coast', 'ivory coast', 8.00000, -5.00000, 320, 'XOF', 'https://flagcdn.com/w640/ci.png', 'https://flagcdn.com/w160/ci.png'),
    ('CK', 'Cook Islands', 'cook islands', -21.23333, -159.76667, 9, 'CKD', 'https://flagcdn.com/w640/ck.png', 'https://flagcdn.com/w160/ck.png'),
    ('CL', 'Chile', 'chile', -30.00000, -71.00000, 491, 'CLP', 'https://flagcdn.com/w640/cl.png', 'https://flagcdn.com/w160/cl.png'),
    ('CM', 'Cameroon', 'cameroon', 6.00000, 12.00000, 389, 'XAF', 'https://flagcdn.com/w640/cm.png', 'https://flagcdn.com/w160/cm.png'),
    ('CN', 'China', 'china', 35.00000, 105.00000, 500, 'CNY', 'https://flagcdn.com/w640/cn.png', 'https://flagcdn.com/w160/cn.png'),
    ('CO', 'Colombia', 'colombia', 4.00000, -72.00000, 500, 'COP', 'https://flagcdn.com/w640/co.png', 'https://flagcdn.com/w160/co.png'),
    ('CR', 'Costa Rica', 'costa rica', 10.00000, -84.00000, 128, 'CRC', 'https://flagcdn.com/w640/cr.png', 'https://flagcdn.com/w160/cr.png'),
    ('CU', 'Cuba', 'cuba', 21.50000, -80.00000, 187, 'CUP', 'https://flagcdn.com/w640/cu.png', 'https://flagcdn.com/w160/cu.png'),
    ('CV', 'Cape Verde', 'cape verde', 16.00000, -24.00000, 36, 'CVE', 'https://flagcdn.com/w640/cv.png', 'https://flagcdn.com/w160/cv.png'),
    ('CW', 'Curaçao', 'curacao', 12.11667, -68.93333, 12, 'XCG', 'https://flagcdn.com/w640/cw.png', 'https://flagcdn.com/w160/cw.png'),
    ('CX', 'Christmas Island', 'christmas island', -10.50000, 105.66667, 7, 'AUD', 'https://flagcdn.com/w640/cx.png', 'https://flagcdn.com/w160/cx.png'),
    ('CY', 'Cyprus', 'cyprus', 35.00000, 33.00000, 54, 'EUR', 'https://flagcdn.com/w640/cy.png', 'https://flagcdn.com/w160/cy.png'),
    ('CZ', 'Czechia', 'czechia', 49.75000, 15.50000, 158, 'CZK', 'https://flagcdn.com/w640/cz.png', 'https://flagcdn.com/w160/cz.png'),
    ('DE', 'Germany', 'germany', 51.00000, 9.00000, 337, 'EUR', 'https://flagcdn.com/w640/de.png', 'https://flagcdn.com/w160/de.png'),
    ('DJ', 'Djibouti', 'djibouti', 11.50000, 43.00000, 86, 'DJF', 'https://flagcdn.com/w640/dj.png', 'https://flagcdn.com/w160/dj.png'),
    ('DK', 'Denmark', 'denmark', 56.00000, 10.00000, 117, 'DKK', 'https://flagcdn.com/w640/dk.png', 'https://flagcdn.com/w160/dk.png'),
    ('DM', 'Dominica', 'dominica', 15.41667, -61.33333, 15, 'XCD', 'https://flagcdn.com/w640/dm.png', 'https://flagcdn.com/w160/dm.png'),
    ('DO', 'Dominican Republic', 'dominican republic', 19.00000, -70.66667, 124, 'DOP', 'https://flagcdn.com/w640/do.png', 'https://flagcdn.com/w160/do.png'),
    ('DZ', 'Algeria', 'algeria', 28.00000, 3.00000, 500, 'DZD', 'https://flagcdn.com/w640/dz.png', 'https://flagcdn.com/w160/dz.png'),
    ('EC', 'Ecuador', 'ecuador', -2.00000, -77.50000, 297, 'USD', 'https://flagcdn.com/w640/ec.png', 'https://flagcdn.com/w160/ec.png'),
    ('EE', 'Estonia', 'estonia', 59.00000, 26.00000, 120, 'EUR', 'https://flagcdn.com/w640/ee.png', 'https://flagcdn.com/w160/ee.png'),
    ('EG', 'Egypt', 'egypt', 27.00000, 30.00000, 500, 'EGP', 'https://flagcdn.com/w640/eg.png', 'https://flagcdn.com/w160/eg.png'),
    ('EH', 'Western Sahara', 'western sahara', 24.50000, -13.00000, 291, 'DZD', 'https://flagcdn.com/w640/eh.png', 'https://flagcdn.com/w160/eh.png'),
    ('ER', 'Eritrea', 'eritrea', 15.00000, 39.00000, 193, 'ERN', 'https://flagcdn.com/w640/er.png', 'https://flagcdn.com/w160/er.png'),
    ('ES', 'Spain', 'spain', 40.00000, -4.00000, 401, 'EUR', 'https://flagcdn.com/w640/es.png', 'https://flagcdn.com/w160/es.png'),
    ('ET', 'Ethiopia', 'ethiopia', 8.00000, 38.00000, 500, 'ETB', 'https://flagcdn.com/w640/et.png', 'https://flagcdn.com/w160/et.png'),
    ('FI', 'Finland', 'finland', 64.00000, 26.00000, 328, 'EUR', 'https://flagcdn.com/w640/fi.png', 'https://flagcdn.com/w160/fi.png'),
    ('FJ', 'Fiji', 'fiji', -18.00000, 175.00000, 76, 'FJD', 'https://flagcdn.com/w640/fj.png', 'https://flagcdn.com/w160/fj.png'),
    ('FK', 'Falkland Islands', 'falkland islands', -51.75000, -59.00000, 62, 'FKP', 'https://flagcdn.com/w640/fk.png', 'https://flagcdn.com/w160/fk.png'),
    ('FM', 'Micronesia', 'micronesia', 6.91667, 158.25000, 15, NULL, 'https://flagcdn.com/w640/fm.png', 'https://flagcdn.com/w160/fm.png'),
    ('FO', 'Faroe Islands', 'faroe islands', 62.00000, -7.00000, 21, 'DKK', 'https://flagcdn.com/w640/fo.png', 'https://flagcdn.com/w160/fo.png'),
    ('FR', 'France', 'france', 46.00000, 2.00000, 419, 'EUR', 'https://flagcdn.com/w640/fr.png', 'https://flagcdn.com/w160/fr.png'),
    ('GA', 'Gabon', 'gabon', -1.00000, 11.75000, 292, 'XAF', 'https://flagcdn.com/w640/ga.png', 'https://flagcdn.com/w160/ga.png'),
    ('GB', 'United Kingdom', 'united kingdom', 54.00000, -2.00000, 278, 'GBP', 'https://flagcdn.com/w640/gb.png', 'https://flagcdn.com/w160/gb.png'),
    ('GD', 'Grenada', 'grenada', 12.11667, -61.66667, 10, 'XCD', 'https://flagcdn.com/w640/gd.png', 'https://flagcdn.com/w160/gd.png'),
    ('GE', 'Georgia', 'georgia', 42.00000, 43.50000, 149, 'GEL', 'https://flagcdn.com/w640/ge.png', 'https://flagcdn.com/w160/ge.png'),
    ('GF', 'French Guiana', 'french guiana', 4.00000, -53.00000, 163, 'EUR', 'https://flagcdn.com/w640/gf.png', 'https://flagcdn.com/w160/gf.png'),
    ('GG', 'Guernsey', 'guernsey', 49.46667, -2.58333, 5, 'GBP', 'https://flagcdn.com/w640/gg.png', 'https://flagcdn.com/w160/gg.png'),
    ('GH', 'Ghana', 'ghana', 8.00000, -2.00000, 276, 'GHS', 'https://flagcdn.com/w640/gh.png', 'https://flagcdn.com/w160/gh.png'),
    ('GI', 'Gibraltar', 'gibraltar', 36.13333, -5.35000, 5, 'GIP', 'https://flagcdn.com/w640/gi.png', 'https://flagcdn.com/w160/gi.png'),
    ('GL', 'Greenland', 'greenland', 72.00000, -40.00000, 500, 'DKK', 'https://flagcdn.com/w640/gl.png', 'https://flagcdn.com/w160/gl.png'),
    ('GM', 'Gambia', 'gambia', 13.46667, -16.56667, 58, 'GMD', 'https://flagcdn.com/w640/gm.png', 'https://flagcdn.com/w160/gm.png'),
    ('GN', 'Guinea', 'guinea', 11.00000, -10.00000, 280, 'GNF', 'https://flagcdn.com/w640/gn.png', 'https://flagcdn.com/w160/gn.png'),
    ('GP', 'Guadeloupe', 'guadeloupe', 16.25000, -61.58333, 23, 'EUR', 'https://flagcdn.com/w640/gp.png', 'https://flagcdn.com/w160/gp.png'),
    ('GQ', 'Equatorial Guinea', 'equatorial guinea', 2.00000, 10.00000, 94, 'XAF', 'https://flagcdn.com/w640/gq.png', 'https://flagcdn.com/w160/gq.png'),
    ('GR', 'Greece', 'greece', 39.00000, 22.00000, 205, 'EUR', 'https://flagcdn.com/w640/gr.png', 'https://flagcdn.com/w160/gr.png'),
    ('GT', 'Guatemala', 'guatemala', 15.50000, -90.25000, 186, 'GTQ', 'https://flagcdn.com/w640/gt.png', 'https://flagcdn.com/w160/gt.png'),
    ('GU', 'Guam', 'guam', 13.46667, 144.78333, 13, 'USD', 'https://flagcdn.com/w640/gu.png', 'https://flagcdn.com/w160/gu.png'),
    ('GW', 'Guinea-Bissau', 'guinea-bissau', 12.00000, -15.00000, 107, 'XOF', 'https://flagcdn.com/w640/gw.png', 'https://flagcdn.com/w160/gw.png'),
    ('GY', 'Guyana', 'guyana', 5.00000, -59.00000, 262, 'GYD', 'https://flagcdn.com/w640/gy.png', 'https://flagcdn.com/w160/gy.png'),
    ('HK', 'Hong Kong', 'hong kong', 22.26700, 114.18800, 19, 'HKD', 'https://flagcdn.com/w640/hk.png', 'https://flagcdn.com/w160/hk.png'),
    ('HN', 'Honduras', 'honduras', 15.00000, -86.50000, 189, 'HNL', 'https://flagcdn.com/w640/hn.png', 'https://flagcdn.com/w160/hn.png'),
    ('HR', 'Croatia', 'croatia', 45.16667, 15.50000, 134, 'EUR', 'https://flagcdn.com/w640/hr.png', 'https://flagcdn.com/w160/hr.png'),
    ('HT', 'Haiti', 'haiti', 19.00000, -72.41667, 94, 'HTG', 'https://flagcdn.com/w640/ht.png', 'https://flagcdn.com/w160/ht.png'),
    ('HU', 'Hungary', 'hungary', 47.00000, 20.00000, 172, 'HUF', 'https://flagcdn.com/w640/hu.png', 'https://flagcdn.com/w160/hu.png'),
    ('ID', 'Indonesia', 'indonesia', -5.00000, 120.00000, 500, 'IDR', 'https://flagcdn.com/w640/id.png', 'https://flagcdn.com/w160/id.png'),
    ('IE', 'Ireland', 'ireland', 53.00000, -8.00000, 150, 'EUR', 'https://flagcdn.com/w640/ie.png', 'https://flagcdn.com/w160/ie.png'),
    ('IL', 'Israel', 'israel', 31.47000, 35.13000, 81, 'ILS', 'https://flagcdn.com/w640/il.png', 'https://flagcdn.com/w160/il.png'),
    ('IM', 'Isle of Man', 'isle of man', 54.25000, -4.50000, 13, 'GBP', 'https://flagcdn.com/w640/im.png', 'https://flagcdn.com/w160/im.png'),
    ('IN', 'India', 'india', 20.00000, 77.00000, 500, 'INR', 'https://flagcdn.com/w640/in.png', 'https://flagcdn.com/w160/in.png'),
    ('IQ', 'Iraq', 'iraq', 33.00000, 44.00000, 374, 'IQD', 'https://flagcdn.com/w640/iq.png', 'https://flagcdn.com/w160/iq.png'),
    ('IR', 'Iran', 'iran', 32.00000, 53.00000, 500, 'IRR', 'https://flagcdn.com/w640/ir.png', 'https://flagcdn.com/w160/ir.png'),
    ('IS', 'Iceland', 'iceland', 65.00000, -18.00000, 181, 'ISK', 'https://flagcdn.com/w640/is.png', 'https://flagcdn.com/w160/is.png'),
    ('IT', 'Italy', 'italy', 42.83333, 12.83333, 310, 'EUR', 'https://flagcdn.com/w640/it.png', 'https://flagcdn.com/w160/it.png'),
    ('JE', 'Jersey', 'jersey', 49.25000, -2.16667, 6, 'GBP', 'https://flagcdn.com/w640/je.png', 'https://flagcdn.com/w160/je.png'),
    ('JM', 'Jamaica', 'jamaica', 18.25000, -77.50000, 59, 'JMD', 'https://flagcdn.com/w640/jm.png', 'https://flagcdn.com/w160/jm.png'),
    ('JO', 'Jordan', 'jordan', 31.00000, 36.00000, 169, 'JOD', 'https://flagcdn.com/w640/jo.png', 'https://flagcdn.com/w160/jo.png'),
    ('JP', 'Japan', 'japan', 36.00000, 138.00000, 347, 'JPY', 'https://flagcdn.com/w640/jp.png', 'https://flagcdn.com/w160/jp.png'),
    ('KE', 'Kenya', 'kenya', 1.00000, 38.00000, 430, 'KES', 'https://flagcdn.com/w640/ke.png', 'https://flagcdn.com/w160/ke.png'),
    ('KG', 'Kyrgyzstan', 'kyrgyzstan', 41.00000, 75.00000, 252, 'KGS', 'https://flagcdn.com/w640/kg.png', 'https://flagcdn.com/w160/kg.png'),
    ('KH', 'Cambodia', 'cambodia', 13.00000, 105.00000, 240, 'KHR', 'https://flagcdn.com/w640/kh.png', 'https://flagcdn.com/w160/kh.png'),
    ('KI', 'Kiribati', 'kiribati', 1.41667, 173.00000, 16, 'AUD', 'https://flagcdn.com/w640/ki.png', 'https://flagcdn.com/w160/ki.png'),
    ('KM', 'Comoros', 'comoros', -12.16667, 44.25000, 24, 'KMF', 'https://flagcdn.com/w640/km.png', 'https://flagcdn.com/w160/km.png'),
    ('KN', 'Saint Kitts and Nevis', 'saint kitts and nevis', 17.33333, -62.75000, 9, 'XCD', 'https://flagcdn.com/w640/kn.png', 'https://flagcdn.com/w160/kn.png'),
    ('KP', 'North Korea', 'north korea', 40.00000, 127.00000, 196, 'KPW', 'https://flagcdn.com/w640/kp.png', 'https://flagcdn.com/w160/kp.png'),
    ('KR', 'South Korea', 'south korea', 37.00000, 127.50000, 179, 'KRW', 'https://flagcdn.com/w640/kr.png', 'https://flagcdn.com/w160/kr.png'),
    ('KW', 'Kuwait', 'kuwait', 29.50000, 45.75000, 75, 'KWD', 'https://flagcdn.com/w640/kw.png', 'https://flagcdn.com/w160/kw.png'),
    ('KY', 'Cayman Islands', 'cayman islands', 19.50000, -80.50000, 9, 'KYD', 'https://flagcdn.com/w640/ky.png', 'https://flagcdn.com/w160/ky.png'),
    ('KZ', 'Kazakhstan', 'kazakhstan', 48.00000, 68.00000, 500, 'KZT', 'https://flagcdn.com/w640/kz.png', 'https://flagcdn.com/w160/kz.png'),
    ('LA', 'Laos', 'laos', 18.00000, 105.00000, 275, 'LAK', 'https://flagcdn.com/w640/la.png', 'https://flagcdn.com/w160/la.png'),
    ('LB', 'Lebanon', 'lebanon', 33.83333, 35.83333, 58, 'LBP', 'https://flagcdn.com/w640/lb.png', 'https://flagcdn.com/w160/lb.png'),
    ('LC', 'Saint Lucia', 'saint lucia', 13.88333, -60.96667, 14, 'XCD', 'https://flagcdn.com/w640/lc.png', 'https://flagcdn.com/w160/lc.png'),
    ('LI', 'Liechtenstein', 'liechtenstein', 47.26667, 9.53333, 7, 'CHF', 'https://flagcdn.com/w640/li.png', 'https://flagcdn.com/w160/li.png'),
    ('LK', 'Sri Lanka', 'sri lanka', 7.00000, 81.00000, 145, 'LKR', 'https://flagcdn.com/w640/lk.png', 'https://flagcdn.com/w160/lk.png'),
    ('LR', 'Liberia', 'liberia', 6.50000, -9.50000, 188, 'LRD', 'https://flagcdn.com/w640/lr.png', 'https://flagcdn.com/w160/lr.png'),
    ('LS', 'Lesotho', 'lesotho', -29.50000, 28.50000, 98, 'LSL', 'https://flagcdn.com/w640/ls.png', 'https://flagcdn.com/w160/ls.png'),
    ('LT', 'Lithuania', 'lithuania', 56.00000, 24.00000, 144, 'EUR', 'https://flagcdn.com/w640/lt.png', 'https://flagcdn.com/w160/lt.png'),
    ('LU', 'Luxembourg', 'luxembourg', 49.75000, 6.16667, 29, 'EUR', 'https://flagcdn.com/w640/lu.png', 'https://flagcdn.com/w160/lu.png'),
    ('LV', 'Latvia', 'latvia', 57.00000, 25.00000, 143, 'EUR', 'https://flagcdn.com/w640/lv.png', 'https://flagcdn.com/w160/lv.png'),
    ('LY', 'Libya', 'libya', 25.00000, 17.00000, 500, 'LYD', 'https://flagcdn.com/w640/ly.png', 'https://flagcdn.com/w160/ly.png'),
    ('MA', 'Morocco', 'morocco', 32.00000, -5.00000, 377, 'MAD', 'https://flagcdn.com/w640/ma.png', 'https://flagcdn.com/w160/ma.png'),
    ('MC', 'Monaco', 'monaco', 43.73333, 7.40000, 5, 'EUR', 'https://flagcdn.com/w640/mc.png', 'https://flagcdn.com/w160/mc.png'),
    ('MD', 'Moldova', 'moldova', 47.00000, 29.00000, 104, 'MDL', 'https://flagcdn.com/w640/md.png', 'https://flagcdn.com/w160/md.png'),
    ('ME', 'Montenegro', 'montenegro', 42.50000, 19.30000, 66, 'EUR', 'https://flagcdn.com/w640/me.png', 'https://flagcdn.com/w160/me.png'),
    ('MF', 'Saint Martin', 'saint martin', 18.08333, -63.95000, 5, 'EUR', 'https://flagcdn.com/w640/mf.png', 'https://flagcdn.com/w160/mf.png'),
    ('MG', 'Madagascar', 'madagascar', -20.00000, 47.00000, 432, 'MGA', 'https://flagcdn.com/w640/mg.png', 'https://flagcdn.com/w160/mg.png'),
    ('MH', 'Marshall Islands', 'marshall islands', 9.00000, 168.00000, 8, 'USD', 'https://flagcdn.com/w640/mh.png', 'https://flagcdn.com/w160/mh.png'),
    ('MK', 'North Macedonia', 'north macedonia', 41.83333, 22.00000, 90, 'MKD', 'https://flagcdn.com/w640/mk.png', 'https://flagcdn.com/w160/mk.png'),
    ('ML', 'Mali', 'mali', 17.00000, -4.00000, 500, 'XOF', 'https://flagcdn.com/w640/ml.png', 'https://flagcdn.com/w160/ml.png'),
    ('MM', 'Myanmar', 'myanmar', 22.00000, 98.00000, 464, 'MMK', 'https://flagcdn.com/w640/mm.png', 'https://flagcdn.com/w160/mm.png'),
    ('MN', 'Mongolia', 'mongolia', 46.00000, 105.00000, 500, 'MNT', 'https://flagcdn.com/w640/mn.png', 'https://flagcdn.com/w160/mn.png'),
    ('MO', 'Macau', 'macau', 22.16667, 113.55000, 5, 'MOP', 'https://flagcdn.com/w640/mo.png', 'https://flagcdn.com/w160/mo.png'),
    ('MP', 'Northern Mariana Islands', 'northern mariana islands', 15.20000, 145.75000, 12, 'USD', 'https://flagcdn.com/w640/mp.png', 'https://flagcdn.com/w160/mp.png'),
    ('MQ', 'Martinique', 'martinique', 14.66667, -61.00000, 19, 'EUR', 'https://flagcdn.com/w640/mq.png', 'https://flagcdn.com/w160/mq.png'),
    ('MR', 'Mauritania', 'mauritania', 20.00000, -12.00000, 500, 'MRU', 'https://flagcdn.com/w640/mr.png', 'https://flagcdn.com/w160/mr.png'),
    ('MS', 'Montserrat', 'montserrat', 16.75000, -62.20000, 6, 'XCD', 'https://flagcdn.com/w640/ms.png', 'https://flagcdn.com/w160/ms.png'),
    ('MT', 'Malta', 'malta', 35.83333, 14.58333, 10, 'EUR', 'https://flagcdn.com/w640/mt.png', 'https://flagcdn.com/w160/mt.png'),
    ('MU', 'Mauritius', 'mauritius', -20.28333, 57.55000, 25, 'MUR', 'https://flagcdn.com/w640/mu.png', 'https://flagcdn.com/w160/mu.png'),
    ('MV', 'Maldives', 'maldives', 3.25000, 73.00000, 10, 'MVR', 'https://flagcdn.com/w640/mv.png', 'https://flagcdn.com/w160/mv.png'),
    ('MW', 'Malawi', 'malawi', -13.50000, 34.00000, 194, 'MWK', 'https://flagcdn.com/w640/mw.png', 'https://flagcdn.com/w160/mw.png'),
    ('MX', 'Mexico', 'mexico', 23.00000, -102.00000, 500, 'MXN', 'https://flagcdn.com/w640/mx.png', 'https://flagcdn.com/w160/mx.png'),
    ('MY', 'Malaysia', 'malaysia', 2.50000, 112.50000, 324, 'MYR', 'https://flagcdn.com/w640/my.png', 'https://flagcdn.com/w160/my.png'),
    ('MZ', 'Mozambique', 'mozambique', -18.25000, 35.00000, 500, 'MZN', 'https://flagcdn.com/w640/mz.png', 'https://flagcdn.com/w160/mz.png'),
    ('NA', 'Namibia', 'namibia', -22.00000, 17.00000, 500, 'NAD', 'https://flagcdn.com/w640/na.png', 'https://flagcdn.com/w160/na.png'),
    ('NC', 'New Caledonia', 'new caledonia', -21.50000, 165.50000, 77, 'XPF', 'https://flagcdn.com/w640/nc.png', 'https://flagcdn.com/w160/nc.png'),
    ('NE', 'Niger', 'niger', 16.00000, 8.00000, 500, 'XOF', 'https://flagcdn.com/w640/ne.png', 'https://flagcdn.com/w160/ne.png'),
    ('NF', 'Norfolk Island', 'norfolk island', -29.03333, 167.95000, 5, 'AUD', 'https://flagcdn.com/w640/nf.png', 'https://flagcdn.com/w160/nf.png'),
    ('NG', 'Nigeria', 'nigeria', 10.00000, 8.00000, 500, 'NGN', 'https://flagcdn.com/w640/ng.png', 'https://flagcdn.com/w160/ng.png'),
    ('NI', 'Nicaragua', 'nicaragua', 13.00000, -85.00000, 204, 'NIO', 'https://flagcdn.com/w640/ni.png', 'https://flagcdn.com/w160/ni.png'),
    ('NL', 'Netherlands', 'netherlands', 52.50000, 5.75000, 115, 'EUR', 'https://flagcdn.com/w640/nl.png', 'https://flagcdn.com/w160/nl.png'),
    ('NO', 'Norway', 'norway', 62.00000, 10.00000, 321, 'NOK', 'https://flagcdn.com/w640/no.png', 'https://flagcdn.com/w160/no.png'),
    ('NP', 'Nepal', 'nepal', 28.00000, 84.00000, 216, 'NPR', 'https://flagcdn.com/w640/np.png', 'https://flagcdn.com/w160/np.png'),
    ('NR', 'Nauru', 'nauru', -0.53333, 166.91667, 5, 'AUD', 'https://flagcdn.com/w640/nr.png', 'https://flagcdn.com/w160/nr.png'),
    ('NU', 'Niue', 'niue', -19.03333, -169.86667, 9, 'NZD', 'https://flagcdn.com/w640/nu.png', 'https://flagcdn.com/w160/nu.png'),
    ('NZ', 'New Zealand', 'new zealand', -41.00000, 174.00000, 293, 'NZD', 'https://flagcdn.com/w640/nz.png', 'https://flagcdn.com/w160/nz.png'),
    ('OM', 'Oman', 'oman', 21.00000, 57.00000, 314, 'OMR', 'https://flagcdn.com/w640/om.png', 'https://flagcdn.com/w160/om.png'),
    ('PA', 'Panama', 'panama', 9.00000, -80.00000, 155, 'PAB', 'https://flagcdn.com/w640/pa.png', 'https://flagcdn.com/w160/pa.png'),
    ('PE', 'Peru', 'peru', -10.00000, -76.00000, 500, 'PEN', 'https://flagcdn.com/w640/pe.png', 'https://flagcdn.com/w160/pe.png'),
    ('PF', 'French Polynesia', 'french polynesia', -15.00000, -140.00000, 36, 'XPF', 'https://flagcdn.com/w640/pf.png', 'https://flagcdn.com/w160/pf.png'),
    ('PG', 'Papua New Guinea', 'papua new guinea', -6.00000, 147.00000, 384, 'PGK', 'https://flagcdn.com/w640/pg.png', 'https://flagcdn.com/w160/pg.png'),
    ('PH', 'Philippines', 'philippines', 13.00000, 122.00000, 330, 'PHP', 'https://flagcdn.com/w640/ph.png', 'https://flagcdn.com/w160/ph.png'),
    ('PK', 'Pakistan', 'pakistan', 30.00000, 70.00000, 500, 'PKR', 'https://flagcdn.com/w640/pk.png', 'https://flagcdn.com/w160/pk.png'),
    ('PL', 'Poland', 'poland', 52.00000, 20.00000, 315, 'PLN', 'https://flagcdn.com/w640/pl.png', 'https://flagcdn.com/w160/pl.png'),
    ('PM', 'Saint Pierre and Miquelon', 'saint pierre and miquelon', 46.83333, -56.33333, 9, 'EUR', 'https://flagcdn.com/w640/pm.png', 'https://flagcdn.com/w160/pm.png'),
    ('PN', 'Pitcairn Islands', 'pitcairn islands', -25.06667, -130.10000, 5, 'NZD', 'https://flagcdn.com/w640/pn.png', 'https://flagcdn.com/w160/pn.png'),
    ('PR', 'Puerto Rico', 'puerto rico', 18.25000, -66.50000, 53, 'USD', 'https://flagcdn.com/w640/pr.png', 'https://flagcdn.com/w160/pr.png'),
    ('PS', 'Palestine', 'palestine', 31.90000, 35.20000, 44, 'EGP', 'https://flagcdn.com/w640/ps.png', 'https://flagcdn.com/w160/ps.png'),
    ('PT', 'Portugal', 'portugal', 39.50000, -8.00000, 171, 'EUR', 'https://flagcdn.com/w640/pt.png', 'https://flagcdn.com/w160/pt.png'),
    ('PW', 'Palau', 'palau', 7.50000, 134.50000, 12, 'USD', 'https://flagcdn.com/w640/pw.png', 'https://flagcdn.com/w160/pw.png'),
    ('PY', 'Paraguay', 'paraguay', -23.00000, -58.00000, 360, 'PYG', 'https://flagcdn.com/w640/py.png', 'https://flagcdn.com/w160/py.png'),
    ('QA', 'Qatar', 'qatar', 25.50000, 51.25000, 61, 'QAR', 'https://flagcdn.com/w640/qa.png', 'https://flagcdn.com/w160/qa.png'),
    ('RE', 'Réunion', 'reunion', -21.15000, 55.50000, 28, 'EUR', 'https://flagcdn.com/w640/re.png', 'https://flagcdn.com/w160/re.png'),
    ('RO', 'Romania', 'romania', 46.00000, 25.00000, 275, 'RON', 'https://flagcdn.com/w640/ro.png', 'https://flagcdn.com/w160/ro.png'),
    ('RS', 'Serbia', 'serbia', 44.00000, 21.00000, 168, 'RSD', 'https://flagcdn.com/w640/rs.png', 'https://flagcdn.com/w160/rs.png'),
    ('RU', 'Russia', 'russia', 60.00000, 100.00000, 500, 'RUB', 'https://flagcdn.com/w640/ru.png', 'https://flagcdn.com/w160/ru.png'),
    ('RW', 'Rwanda', 'rwanda', -2.00000, 30.00000, 92, 'RWF', 'https://flagcdn.com/w640/rw.png', 'https://flagcdn.com/w160/rw.png'),
    ('SA', 'Saudi Arabia', 'saudi arabia', 25.00000, 45.00000, 500, 'SAR', 'https://flagcdn.com/w640/sa.png', 'https://flagcdn.com/w160/sa.png'),
    ('SB', 'Solomon Islands', 'solomon islands', -8.00000, 159.00000, 96, 'SBD', 'https://flagcdn.com/w640/sb.png', 'https://flagcdn.com/w160/sb.png'),
    ('SC', 'Seychelles', 'seychelles', -4.58333, 55.66667, 12, 'SCR', 'https://flagcdn.com/w640/sc.png', 'https://flagcdn.com/w160/sc.png'),
    ('SD', 'Sudan', 'sudan', 15.00000, 30.00000, 500, 'SDG', 'https://flagcdn.com/w640/sd.png', 'https://flagcdn.com/w160/sd.png'),
    ('SE', 'Sweden', 'sweden', 62.00000, 15.00000, 379, 'SEK', 'https://flagcdn.com/w640/se.png', 'https://flagcdn.com/w160/se.png'),
    ('SG', 'Singapore', 'singapore', 1.36667, 103.80000, 15, 'SGD', 'https://flagcdn.com/w640/sg.png', 'https://flagcdn.com/w160/sg.png'),
    ('SH', 'Saint Helena, Ascension and Tristan da Cunha', 'saint helena, ascension and tristan da cunha', -15.95000, -5.72000, 11, 'GBP', 'https://flagcdn.com/w640/sh.png', 'https://flagcdn.com/w160/sh.png'),
    ('SI', 'Slovenia', 'slovenia', 46.11667, 14.81667, 80, 'EUR', 'https://flagcdn.com/w640/si.png', 'https://flagcdn.com/w160/si.png'),
    ('SJ', 'Svalbard and Jan Mayen', 'svalbard and jan mayen', 78.00000, 20.00000, 6, 'NOK', 'https://flagcdn.com/w640/sj.png', 'https://flagcdn.com/w160/sj.png'),
    ('SK', 'Slovakia', 'slovakia', 48.66667, 19.50000, 125, 'EUR', 'https://flagcdn.com/w640/sk.png', 'https://flagcdn.com/w160/sk.png'),
    ('SL', 'Sierra Leone', 'sierra leone', 8.50000, -11.50000, 151, 'SLE', 'https://flagcdn.com/w640/sl.png', 'https://flagcdn.com/w160/sl.png'),
    ('SM', 'San Marino', 'san marino', 43.76667, 12.41667, 5, 'EUR', 'https://flagcdn.com/w640/sm.png', 'https://flagcdn.com/w160/sm.png'),
    ('SN', 'Senegal', 'senegal', 14.00000, -14.00000, 250, 'XOF', 'https://flagcdn.com/w640/sn.png', 'https://flagcdn.com/w160/sn.png'),
    ('SO', 'Somalia', 'somalia', 10.00000, 49.00000, 451, 'SOS', 'https://flagcdn.com/w640/so.png', 'https://flagcdn.com/w160/so.png'),
    ('SR', 'Suriname', 'suriname', 4.00000, -56.00000, 228, 'SRD', 'https://flagcdn.com/w640/sr.png', 'https://flagcdn.com/w160/sr.png'),
    ('SS', 'South Sudan', 'south sudan', 7.00000, 30.00000, 444, 'SSP', 'https://flagcdn.com/w640/ss.png', 'https://flagcdn.com/w160/ss.png'),
    ('ST', 'São Tomé and Príncipe', 'sao tome and principe', 1.00000, 7.00000, 18, 'STN', 'https://flagcdn.com/w640/st.png', 'https://flagcdn.com/w160/st.png'),
    ('SV', 'El Salvador', 'el salvador', 13.83333, -88.91667, 82, 'USD', 'https://flagcdn.com/w640/sv.png', 'https://flagcdn.com/w160/sv.png'),
    ('SX', 'Sint Maarten', 'sint maarten', 18.03333, -63.05000, 5, 'XCG', 'https://flagcdn.com/w640/sx.png', 'https://flagcdn.com/w160/sx.png'),
    ('SY', 'Syria', 'syria', 35.00000, 38.00000, 243, 'SYP', 'https://flagcdn.com/w640/sy.png', 'https://flagcdn.com/w160/sy.png'),
    ('SZ', 'Eswatini', 'eswatini', -26.50000, 31.50000, 74, 'SZL', 'https://flagcdn.com/w640/sz.png', 'https://flagcdn.com/w160/sz.png'),
    ('TC', 'Turks and Caicos Islands', 'turks and caicos islands', 21.75000, -71.58333, 17, 'USD', 'https://flagcdn.com/w640/tc.png', 'https://flagcdn.com/w160/tc.png'),
    ('TD', 'Chad', 'chad', 15.00000, 19.00000, 500, 'XAF', 'https://flagcdn.com/w640/td.png', 'https://flagcdn.com/w160/td.png'),
    ('TG', 'Togo', 'togo', 8.00000, 1.16667, 134, 'XOF', 'https://flagcdn.com/w640/tg.png', 'https://flagcdn.com/w160/tg.png'),
    ('TH', 'Thailand', 'thailand', 15.00000, 100.00000, 404, 'THB', 'https://flagcdn.com/w640/th.png', 'https://flagcdn.com/w160/th.png'),
    ('TJ', 'Tajikistan', 'tajikistan', 39.00000, 71.00000, 213, 'TJS', 'https://flagcdn.com/w640/tj.png', 'https://flagcdn.com/w160/tj.png'),
    ('TK', 'Tokelau', 'tokelau', -9.00000, -172.00000, 5, 'NZD', 'https://flagcdn.com/w640/tk.png', 'https://flagcdn.com/w160/tk.png'),
    ('TL', 'Timor-Leste', 'timor-leste', -8.83333, 125.91667, 69, 'USD', 'https://flagcdn.com/w640/tl.png', 'https://flagcdn.com/w160/tl.png'),
    ('TM', 'Turkmenistan', 'turkmenistan', 40.00000, 60.00000, 394, 'TMT', 'https://flagcdn.com/w640/tm.png', 'https://flagcdn.com/w160/tm.png'),
    ('TN', 'Tunisia', 'tunisia', 34.00000, 9.00000, 228, 'TND', 'https://flagcdn.com/w640/tn.png', 'https://flagcdn.com/w160/tn.png'),
    ('TO', 'Tonga', 'tonga', -20.00000, -175.00000, 15, 'TOP', 'https://flagcdn.com/w640/to.png', 'https://flagcdn.com/w160/to.png'),
    ('TR', 'Türkiye', 'turkiye', 39.00000, 35.00000, 499, 'TRY', 'https://flagcdn.com/w640/tr.png', 'https://flagcdn.com/w160/tr.png'),
    ('TT', 'Trinidad and Tobago', 'trinidad and tobago', 11.00000, -61.00000, 40, 'TTD', 'https://flagcdn.com/w640/tt.png', 'https://flagcdn.com/w160/tt.png'),
    ('TV', 'Tuvalu', 'tuvalu', -8.00000, 178.00000, 5, 'AUD', 'https://flagcdn.com/w640/tv.png', 'https://flagcdn.com/w160/tv.png'),
    ('TW', 'Taiwan', 'taiwan', 23.50000, 121.00000, 107, 'TWD', 'https://flagcdn.com/w640/tw.png', 'https://flagcdn.com/w160/tw.png'),
    ('TZ', 'Tanzania', 'tanzania', -6.00000, 35.00000, 500, 'TZS', 'https://flagcdn.com/w640/tz.png', 'https://flagcdn.com/w160/tz.png'),
    ('UA', 'Ukraine', 'ukraine', 49.00000, 32.00000, 438, 'UAH', 'https://flagcdn.com/w640/ua.png', 'https://flagcdn.com/w160/ua.png'),
    ('UG', 'Uganda', 'uganda', 1.00000, 32.00000, 277, 'UGX', 'https://flagcdn.com/w640/ug.png', 'https://flagcdn.com/w160/ug.png'),
    ('US', 'United States', 'united states', 38.00000, -97.00000, 500, 'USD', 'https://flagcdn.com/w640/us.png', 'https://flagcdn.com/w160/us.png'),
    ('UY', 'Uruguay', 'uruguay', -33.00000, -56.00000, 240, 'UYU', 'https://flagcdn.com/w640/uy.png', 'https://flagcdn.com/w160/uy.png'),
    ('UZ', 'Uzbekistan', 'uzbekistan', 41.00000, 64.00000, 377, 'UZS', 'https://flagcdn.com/w640/uz.png', 'https://flagcdn.com/w160/uz.png'),
    ('VA', 'Vatican City', 'vatican city', 41.90000, 12.45000, 5, 'EUR', 'https://flagcdn.com/w640/va.png', 'https://flagcdn.com/w160/va.png'),
    ('VC', 'Saint Vincent and the Grenadines', 'saint vincent and the grenadines', 13.25000, -61.20000, 11, 'XCD', 'https://flagcdn.com/w640/vc.png', 'https://flagcdn.com/w160/vc.png'),
    ('VE', 'Venezuela', 'venezuela', 8.00000, -66.00000, 500, 'VES', 'https://flagcdn.com/w640/ve.png', 'https://flagcdn.com/w160/ve.png'),
    ('VG', 'British Virgin Islands', 'british virgin islands', 18.43138, -64.62305, 7, 'USD', 'https://flagcdn.com/w640/vg.png', 'https://flagcdn.com/w160/vg.png'),
    ('VI', 'United States Virgin Islands', 'united states virgin islands', 18.35000, -64.93333, 11, 'USD', 'https://flagcdn.com/w640/vi.png', 'https://flagcdn.com/w160/vi.png'),
    ('VN', 'Vietnam', 'vietnam', 16.16667, 107.83333, 325, 'VND', 'https://flagcdn.com/w640/vn.png', 'https://flagcdn.com/w160/vn.png'),
    ('VU', 'Vanuatu', 'vanuatu', -16.00000, 167.00000, 62, 'VUV', 'https://flagcdn.com/w640/vu.png', 'https://flagcdn.com/w160/vu.png'),
    ('WF', 'Wallis and Futuna', 'wallis and futuna', -13.30000, -176.20000, 7, 'XPF', 'https://flagcdn.com/w640/wf.png', 'https://flagcdn.com/w160/wf.png'),
    ('WS', 'Samoa', 'samoa', -13.58333, -172.33333, 30, 'WST', 'https://flagcdn.com/w640/ws.png', 'https://flagcdn.com/w160/ws.png'),
    ('XK', 'Kosovo', 'kosovo', 42.66667, 21.16667, 59, 'EUR', 'https://flagcdn.com/w640/xk.png', 'https://flagcdn.com/w160/xk.png'),
    ('YE', 'Yemen', 'yemen', 15.00000, 48.00000, 410, 'YER', 'https://flagcdn.com/w640/ye.png', 'https://flagcdn.com/w160/ye.png'),
    ('YT', 'Mayotte', 'mayotte', -12.83333, 45.16667, 11, 'EUR', 'https://flagcdn.com/w640/yt.png', 'https://flagcdn.com/w160/yt.png'),
    ('ZA', 'South Africa', 'south africa', -29.00000, 24.00000, 500, 'ZAR', 'https://flagcdn.com/w640/za.png', 'https://flagcdn.com/w160/za.png'),
    ('ZM', 'Zambia', 'zambia', -15.00000, 30.00000, 489, 'ZMW', 'https://flagcdn.com/w640/zm.png', 'https://flagcdn.com/w160/zm.png'),
    ('ZW', 'Zimbabwe', 'zimbabwe', -20.00000, 30.00000, 353, 'BWP', 'https://flagcdn.com/w640/zw.png', 'https://flagcdn.com/w160/zw.png')
) AS c (code, name, normalized, lat, lng, radius, currency, image_url, avatar_url);

-- The countries travellers pick most lead a list that has nothing typed in it.
UPDATE location_images country
SET priority = popular.priority
FROM (VALUES
    ('VN', 100), ('TH', 95), ('JP', 94), ('KR', 93), ('SG', 92), ('CN', 91), ('TW', 90), ('MY', 89),
    ('ID', 88), ('HK', 87), ('US', 86), ('FR', 85), ('IT', 84), ('ES', 83), ('GB', 82), ('AU', 81),
    ('DE', 80), ('KH', 79), ('LA', 78), ('PH', 77), ('IN', 76), ('AE', 75), ('TR', 74), ('CH', 73),
    ('NL', 72), ('GR', 71), ('PT', 70), ('CA', 69), ('NZ', 68), ('MV', 67), ('MO', 66), ('AT', 65),
    ('CZ', 64), ('EG', 63), ('MX', 62), ('BR', 61), ('RU', 60), ('MM', 59), ('NP', 58), ('LK', 57)
) AS popular (code, priority)
WHERE country.kind = 'COUNTRY'
  AND country.country_code = popular.code;

-- Every existing row is a Vietnamese travel region.
UPDATE location_images region
SET parent_id = country.id,
    currency_code = 'VND'
FROM location_images country
WHERE country.kind = 'COUNTRY'
  AND country.country_code = 'VN'
  AND region.kind = 'REGION';

-- A region's name in the app's other languages ("Hà Nội" / "Hanoi" / "ハノイ").
-- Countries need none: their names come from the country code.
CREATE TABLE location_image_translations (
    location_image_id UUID NOT NULL REFERENCES location_images (id) ON DELETE CASCADE,
    locale VARCHAR(10) NOT NULL,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (location_image_id, locale)
);

-- Where a user is from: their country. It decides what
-- a destination picker lists first (a Vietnamese user sees Vietnam's regions, not
-- "Vietnam"). Every account so far was created in Vietnam.
ALTER TABLE users
    ADD COLUMN home_country_code VARCHAR(2);

UPDATE users SET home_country_code = 'VN' WHERE home_country_code IS NULL;

-- The two resolvers, unchanged except that they read REGION rows only.
CREATE OR REPLACE FUNCTION goroute_nearest_location_image(p_lat NUMERIC, p_lng NUMERIC)
RETURNS UUID
LANGUAGE sql
STABLE
AS $$
    SELECT candidate.id
    FROM (
        SELECT li.id,
               li.coverage_radius_km,
               6371 * acos(LEAST(1, GREATEST(-1,
                   cos(radians(li.latitude)) * cos(radians(p_lat)) *
                   cos(radians(p_lng) - radians(li.longitude)) +
                   sin(radians(li.latitude)) * sin(radians(p_lat))
               ))) AS distance_km
        FROM location_images li
        WHERE p_lat IS NOT NULL
          AND p_lng IS NOT NULL
          AND li.kind = 'REGION'
          AND li.latitude IS NOT NULL
          AND li.longitude IS NOT NULL
    ) candidate
    WHERE candidate.distance_km <= candidate.coverage_radius_km
    ORDER BY candidate.distance_km
    LIMIT 1
$$;

CREATE OR REPLACE FUNCTION goroute_location_image_for_point(p_lat NUMERIC, p_lng NUMERIC)
RETURNS UUID
LANGUAGE sql
STABLE
AS $$
    SELECT COALESCE(
        (SELECT liw.location_image_id
         FROM wards w
         JOIN location_image_wards liw ON liw.ward_code = w.code
         JOIN location_images li ON li.id = liw.location_image_id AND li.kind = 'REGION'
         WHERE p_lat IS NOT NULL AND p_lng IS NOT NULL
           AND ST_Covers(w.geom, ST_SetSRID(ST_MakePoint(p_lng::float8, p_lat::float8), 4326))
         ORDER BY (SELECT COUNT(*) FROM location_image_wards peer
                   WHERE peer.location_image_id = liw.location_image_id),
                  CASE
                      WHEN li.latitude IS NULL OR li.longitude IS NULL THEN NULL
                      ELSE ST_Distance(
                          ST_SetSRID(ST_MakePoint(li.longitude::float8, li.latitude::float8), 4326)::geography,
                          ST_SetSRID(ST_MakePoint(p_lng::float8, p_lat::float8), 4326)::geography)
                  END NULLS LAST,
                  liw.location_image_id
         LIMIT 1),
        goroute_nearest_location_image(p_lat, p_lng))
$$;
