# Place Translation Design

## Goal

Place data is split into two parts:

- `places`: language-independent fields such as coordinates, category, address, rating, images, opening hours, price range, and Google Maps IDs.
- `place_translations`: language-dependent fields such as `name` and `description`.

This keeps the API flexible for multi-language UI while keeping the core place record stable.

## Table Convention

For any entity that needs translatable UI text, follow this naming pattern:

- Main table: `places`, `foods`, `categories`
- Translation table: `place_translations`, `food_translations`, `category_translations`
- Translation entity: `PlaceTranslation`, `FoodTranslation`, `CategoryTranslation`

Every translation table should have:

- Foreign key to the main table, for example `place_id`
- `locale`, for example `vi`, `en`, `ja`, `zh-TW`
- Translatable fields, for example `name`, `description`
- `translation_source`, currently `MANUAL` or `LEGACY`
- Unique constraint on `(entity_id, locale)`

For places, the current table is:

```sql
place_translations (
    id,
    place_id,
    locale,
    name,
    description,
    translation_source,
    created_at,
    updated_at
)
```

## Locale Rules

Supported locales are defined in `TranslationLocale`, not hard-coded in services:

```text
vi, en, th, ja, ko, zh-TW, ru, hi, de, es, pt
```

Default insert/update source language:

```text
vi
```

Read fallback order:

```text
requested locale -> en -> vi/base place data
```

Example:

- Client sends `Accept-Language: ja`
- If `ja` exists, API returns Japanese `name` and `descriptions`
- If `ja` is missing, API tries `en`
- If `en` is missing, API uses `vi`
- If even translation rows are missing, API falls back to `places.title` and `places.descriptions`

## API Shape

The API response is intentionally flattened for mobile clients.

Clients receive:

```json
{
  "id": "...",
  "placeId": "...",
  "locale": "en",
  "name": "Hoan Kiem Lake",
  "title": "Hoan Kiem Lake",
  "descriptions": "...",
  "translations": {
    "vi": {
      "locale": "vi",
      "name": "Hồ Hoàn Kiếm",
      "description": "...",
      "translationSource": "MANUAL"
    },
    "en": {
      "locale": "en",
      "name": "Hoan Kiem Lake",
      "description": "...",
      "translationSource": "MANUAL"
    }
  }
}
```

`name`, `title`, and `descriptions` are already resolved for the request locale. The client does not need to know how fallback works.

`translations` is included mainly for admin screens.

## Insert/Update Flow

Place insert/update still writes the main `places` row first.

Then `PlaceTranslationService.syncTranslations(...)` does this:

1. Normalize incoming translation keys through `TranslationLocale`.
2. Resolve the default source from `translations.vi` if provided.
3. If `translations.vi` is missing, use `places.title` and `places.descriptions` as Vietnamese legacy source.
4. Upsert the `vi` translation row.
5. Upsert every manually provided locale.
6. Do not call external translation APIs.

Important: there is no automatic machine translation now. Admin must provide translations manually when needed.

## Admin Screen

`admin-places.html` renders one translation panel per supported locale.

On save/import, it sends:

```json
{
  "title": "Tên tiếng Việt",
  "translations": {
    "vi": {
      "name": "Tên tiếng Việt",
      "description": "Mô tả tiếng Việt"
    },
    "en": {
      "name": "English name",
      "description": "English description"
    }
  }
}
```

If a locale is left empty, it is not sent and no row is created for that locale.

## How To Extend To Another Entity

For `Food`, follow the same pattern:

1. Create `food_translations`.
2. Add `FoodTranslation`.
3. Add `FoodTranslationMapper` and XML mapper.
4. Add a service similar to `PlaceTranslationService`.
5. Keep immutable/non-language fields in `foods`.
6. Move UI text fields such as `name`, `description`, `ingredients`, `variants`, or rich guide sections into `food_translations`.

For complex translated content:

- Use regular columns for simple fields like `name`, `description`.
- Use JSONB/text JSON inside the translation table for large structured content if the app does not need deep SQL querying.

## Files

- `TranslationLocale`: supported locale enum and fallback constants.
- `TranslationSource`: translation source enum.
- `PlaceTranslation`: translation entity.
- `PlaceTranslationMapper`: MyBatis mapper interface.
- `PlaceTranslationMapper.xml`: SQL for upsert and lookup.
- `PlaceTranslationService`: locale normalization, upsert, and fallback resolution.
- `PlaceServiceImpl`: calls translation sync on insert/update and resolves translated response.
- `admin-places.html`: admin UI for manual translation editing.
- `V064__place_translations.sql`: creates translation table and backfills legacy `en`.
- `V065__place_translation_default_vi.sql`: backfills default `vi` without modifying the older migration.
