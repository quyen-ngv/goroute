Batch insert reviews tá»« crawler
- **PlaceReviewScoringService**: TÃ­nh toÃ¡n scores vá»›i multi-threading

### 4. Controllers
- **PlaceReviewController**: 4 endpoints má»›i

## ðŸ“¡ API Endpoints

### 1. Batch Insert Reviews
```http
POST /v1/api/place-reviews/batch
Content-Type: application/json

{
  "reviews": [
    {
      "reviewId": "Ci9DQUlRQUNvZENodHljRjlv...",
      "googlePlaceId": "0x3135ab0062f151a1:0",
      "authorName": "Shin Fuku",
      "profileUrl": "https://www.google.com/maps/contrib/...",
      "profilePicture": "https://lh3.googleusercontent.com/...",
      "isLocalGuide": true,
      "totalReviews": 238,
      "totalPhotos": 979,
      "rating": 5,
      "reviewText": {
        "ja": "å¯ºã®è¦‹å­¦ãŒçµ‚ã‚ã‚Š..."
      },
      "reviewDate": "2026-05-06T16:03:59+00:00",
      "userImages": ["https://..."],
      "likes": 0,
      "contentHash": "bf308c448ecd36b6...",
      "isDeleted": false
    }
  ]
}
```

**Response:**
```json
{
  "code": 200,
  "data": {
    "totalInput": 10,
    "inserted": 8,
    "skipped": 2,
    "failed": 0,
    "errors": []
  }
}
```

### 2. Calculate All Scores (Full Job)
```http
POST /v1/api/place-reviews/calculate-scores?placeId={uuid}&forceRecalculate=false
```

**Parameters:**
- `placeId` (optional): TÃ­nh cho 1 place cá»¥ thá»ƒ, bá» trá»‘ng = tÃ­nh táº¥t cáº£
- `forceRecalculate` (optional): `true` = tÃ­nh láº¡i táº¥t cáº£, `false` = chá»‰ tÃ­nh chÆ°a cÃ³ score

**Response:**
```json
{
  "code": 200,
  "data": {
    "reviewsUpdated": 150,
    "placesUpdated": 25
  }
}
```

### 3. Calculate Review Scores Only (Step 1)
```http
POST /v1/api/place-reviews/calculate-review-scores?placeId={uuid}&forceRecalculate=false
```

Chá»‰ tÃ­nh `authenticity_score` cho reviews.

### 4. Calculate Place Scores Only (Step 2)
```http
POST /v1/api/place-reviews/calculate-place-scores?placeId={uuid}&forceRecalculate=false
```

Chá»‰ tÃ­nh `place_overall_score` vÃ  `adjusted_rating` cho places.

## ðŸ§® CÃ´ng thá»©c tÃ­nh toÃ¡n

### Review Authenticity Score
```
authenticity_score =
  0.15 * has_text
  + 0.25 * text_length_score
  + 0.25 * has_photos
  + 0.35 * reviewer_credibility_score
```

**ThÃ nh pháº§n:**
- `has_text`: 1.0 náº¿u cÃ³ text, 0.0 náº¿u khÃ´ng
- `text_length_score`: min(length / 200, 1.0)
- `has_photos`: 1.0 náº¿u cÃ³ áº£nh, 0.0 náº¿u khÃ´ng
- `reviewer_credibility`: trung bÃ¬nh cá»§a:
  - `r1`: min(total_reviews / 50, 1.0)
  - `r2`: min(total_photos / 100, 1.0)
  - `r3`: is_local_guide ? 1.0 : 0.0

**PhÃ¢n loáº¡i:**
- `>= 0.80`: HIGH
- `0.50 - 0.79`: MEDIUM
- `< 0.50`: LOW

### Place Overall Score
```
place_overall_score =
  0.50 * avg_authenticity_score
  + 0.20 * low_star_signal_score
  + 0.20 * distribution_score
  + 0.10 * spike_penalty_score
```

**ThÃ nh pháº§n:**
- `avg_authenticity_score`: Trung bÃ¬nh authenticity cá»§a táº¥t cáº£ reviews
- `low_star_signal_score`: Dá»±a trÃªn sá»‘ review <= 2 sao cÃ³ authenticity >= 0.5
  - 0 review: 1.0
  - 1 review: 0.7
  - 2 reviews: 0.4
  - >= 3 reviews: 0.0
- `distribution_score`: PhÃ¡t hiá»‡n J-curve
  - Normal: 1.0
  - J-curve (>70% 5 sao, <10% 2-4 sao): 0.2
- `spike_penalty_score`: PhÃ¡t hiá»‡n review flooding
  - Normal: 1.0
  - Spike (max_day > avg_day * 5): 0.2

**PhÃ¢n loáº¡i:**
- `>= 0.80`: TRUSTED
- `0.55 - 0.79`: MODERATE
- `0.30 - 0.54`: CAUTION
- `< 0.30`: SUSPICIOUS

### Adjusted Rating
```
adjusted_rating = review_rating * place_overall_score
```

Chá»‰ hiá»ƒn thá»‹ khi `review_count >= 5`, náº¿u khÃ´ng hiá»ƒn thá»‹ "ChÆ°a Ä‘á»§ dá»¯ liá»‡u".

## ðŸš€ Workflow sá»­ dá»¥ng

### 1. Import reviews tá»« crawler
```bash
POST /v1/api/place-reviews/batch
# Body: danh sÃ¡ch reviews tá»« crawler
```

### 2. TÃ­nh scores
```bash
# Option A: TÃ­nh táº¥t cáº£ (Step 1 + Step 2)
POST /v1/api/place-reviews/calculate-scores

# Option B: TÃ­nh tá»«ng bÆ°á»›c
POST /v1/api/place-reviews/calculate-review-scores  # Step 1
POST /v1/api/place-reviews/calculate-place-scores   # Step 2

# Option C: TÃ­nh cho 1 place cá»¥ thá»ƒ
POST /v1/api/place-reviews/calculate-scores?placeId=xxx-xxx-xxx

# Option D: Force recalculate táº¥t cáº£
POST /v1/api/place-reviews/calculate-scores?forceRecalculate=true
```

### 3. Query places vá»›i adjusted rating
```bash
GET /v1/api/places/search?latitude=...&longitude=...
# Response sáº½ cÃ³ adjustedRating vÃ  trustLevel
```

## âš¡ Performance

- **Multi-threading**: Sá»­ dá»¥ng `ExecutorService` vá»›i sá»‘ thread = sá»‘ CPU cores
- **Batch processing**: Insert reviews theo batch thay vÃ¬ tá»«ng record
- **Selective update**: Chá»‰ tÃ­nh láº¡i reviews/places cáº§n thiáº¿t (dá»±a vÃ o `score_calculated_at`)

## ðŸ—„ï¸ Database Migration

Cháº¡y migration script:
```sql
-- File: V1__add_place_review_scoring_fields.sql
-- Tá»± Ä‘á»™ng cháº¡y khi start app (náº¿u dÃ¹ng Flyway/Liquibase)
```

Script sáº½:
- Táº¡o 4 enum types má»›i
- ThÃªm 9 columns vÃ o `place` table
- ThÃªm 13 columns vÃ o `place_review` table
- Táº¡o indexes cho performance

## ðŸ“Š VÃ­ dá»¥ káº¿t quáº£

### Review vá»›i HIGH authenticity
```json
{
  "reviewId": "xxx",
  "reviewerName": "Shin Fuku",
  "rating": 5,
  "description": "Very detailed review with 200+ characters...",
  "images": ["url1", "url2"],
  "totalReviews": 238,
  "totalPhotos": 979,
  "isLocalGuide": true,
  "authenticityScore": 0.95,
  "authenticityLevel": "HIGH"
}
```

### Place vá»›i TRUSTED level
```json
{
  "title": "BÃºn Cháº£ HÆ°Æ¡ng LiÃªn",
  "reviewRating": 4.5,
  "reviewCount": 1250,
  "avgAuthenticityScore": 0.72,
  "placeOverallScore": 0.85,
  "adjustedRating": 3.8,
  "trustLevel": "TRUSTED",
  "isJcurveDetected": false,
  "isSpikeDetected": false,
  "authenticLowStarCount": 2
}
```

## ðŸ” Monitoring

Check logs Ä‘á»ƒ theo dÃµi:
```
[INFO] Starting full scoring job - placeId: null, forceRecalculate: false
[INFO] Step 1 completed: 150 reviews updated
[INFO] Step 2 completed: 25 places updated
```

## âš ï¸ LÆ°u Ã½

1. **Place pháº£i tá»“n táº¡i trÆ°á»›c**: Khi insert reviews, place vá»›i `googlePlaceId` pháº£i Ä‘Ã£ cÃ³ trong DB
2. **Deduplication**: Dá»±a vÃ o `reviewId` Ä‘á»ƒ trÃ¡nh duplicate
3. **Content change detection**: Dá»±a vÃ o `contentHash` Ä‘á»ƒ detect review bá»‹ edit
4. **Minimum reviews**: `adjusted_rating` chá»‰ hiá»ƒn thá»‹ khi place cÃ³ >= 5 reviews
5. **Stale scores**: NÃªn cháº¡y job tÃ­nh láº¡i scores Ä‘á»‹nh ká»³ (daily/weekly)

## ðŸ› Troubleshooting

### Reviews khÃ´ng Ä‘Æ°á»£c insert
- Check place Ä‘Ã£ tá»“n táº¡i chÆ°a: `GET /v1/api/places/google/{placeId}`
- Check logs Ä‘á»ƒ xem lá»—i cá»¥ thá»ƒ

### Scores khÃ´ng Ä‘Æ°á»£c tÃ­nh
- Check `score_calculated_at` cÃ³ null khÃ´ng
- Thá»­ `forceRecalculate=true`
- Check logs Ä‘á»ƒ xem exception

### Performance cháº­m
- Giáº£m sá»‘ reviews trong 1 batch (khuyáº¿n nghá»‹ < 1000)
- TÄƒng sá»‘ thread trong `ExecutorService`
- ThÃªm indexes cho cÃ¡c columns hay query
