# Administrative boundaries (provinces + wards)

Source: https://github.com/thanglequoc/vietnamese-provinces-database (34 provinces,
3 321 wards, GIS boundaries under `json/vn_provinces_wards_geojson.zip`).

The archive is ~630 MB unpacked, so it is loaded by SQL generated here and run with
`psql`, never through Flyway. `V170__geo_boundaries.sql` creates the tables; this script
fills them; the admin backfill (`POST /v1/api/admin/geo/backfill`) then remaps the old
63-province rows and assigns a ward to every place and check-in.

```bash
# 1. fetch the dataset release
mkdir -p work && cd work
curl -LO https://raw.githubusercontent.com/thanglequoc/vietnamese-provinces-database/master/json/full_json_generated_data_vn_units.json
curl -LO https://raw.githubusercontent.com/thanglequoc/vietnamese-provinces-database/master/json/vn_provinces_metadata.json
curl -LO https://raw.githubusercontent.com/thanglequoc/vietnamese-provinces-database/master/json/vn_provinces_wards_geojson.zip
unzip -q vn_provinces_wards_geojson.zip
cd ..

# 2. build the SQL (~10 minutes; mapshaper simplifies every ward twice)
node build_geo_sql.mjs \
  --units work/full_json_generated_data_vn_units.json \
  --metadata work/vn_provinces_metadata.json \
  --geojson-dir work/geojson \
  --remap remap_63_to_34.json \
  --out ../../data/geo/geo_dataset_v5.1.0.sql

# 3. load (after the app has run V170)
psql "$DB_URL" -v ON_ERROR_STOP=1 -f ../../data/geo/geo_dataset_v5.1.0.sql

# 4. remap + assign wards, from the admin console (Geo dataset card) or:
curl -X POST -H "Authorization: Bearer $ADMIN_TOKEN" https://<host>/v1/api/admin/geo/backfill
```

`remap_63_to_34.json` is the hand-maintained list of which former province each old
code merged into. The script refuses to run if any of the 63 old codes is unmapped or
maps to a code the dataset does not have.

Geometry columns:

| column | resolution | used for |
|---|---|---|
| `geom` | source, 6 decimals | `ST_Covers` point-in-polygon (verification, backfill) |
| `geom_display` | Visvalingam 30 m (wards) / 200 m (provinces), keep-shapes | GeoJSON served to the app |
