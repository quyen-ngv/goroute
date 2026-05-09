# Place API Examples

## 1. Import Single Place from Google Maps Data

```bash
curl -X POST http://localhost:8080/api/places/import \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "placeId": "ChIJI-LkoRfIODERrkzpzgMF7pI",
    "cid": "10587405287909051566",
    "dataId": "0x3138c817a1e4e223:0x92ee0503cee94cae",
    "title": "Hang Sơn Đoòng",
    "category": "Tourist attraction",
    "address": "Bố Trạch, Quảng Bình, Vietnam",
    "latitude": 17.464792,
    "longitude": 106.287394,
    "plusCode": "F77P+WX Thuong Trach, Quảng Bình, Vietnam",
    "timezone": "Asia/Saigon",
    "phone": "",
    "website": "https://sondoongcave.info/",
    "googleMapsLink": "https://www.google.com/maps/place/Hang+S%C6%A1n+%C4%90o%C3%B2ng/@17.4647924,106.2873939,15z",
    "reviewCount": 1131,
    "reviewRating": 4.7,
    "reviewsPerRating": {
      "1": 53,
      "2": 12,
      "3": 30,
      "4": 84,
      "5": 952
    },
    "thumbnail": "https://lh3.googleusercontent.com/gps-cs-s/APNQkAEw3r80UiyqxUVKtBz9LUcM70TiFyj9pR6dtmLEkYEEnxvUhVBn3X0RxcQYMi3dzItvP-Y8jPQYZmfX0cHGuv2kNV0DEq0H9MBuLw-xaYhE1HsVxJiWUoI-NoOrIJvO0LHKVVPHhg=w408-h408-k-no",
    "descriptions": "This enormous mountain cavern dating back millions of years offers guided exploration tours.",
    "priceRange": "",
    "images": [
      {
        "title": "All",
        "image": "https://lh3.googleusercontent.com/..."
      }
    ],
    "completeAddress": {
      "borough": "Bố Trạch",
      "street": "",
      "city": "Quảng Bình",
      "postal_code": "",
      "state": "Bố Trạch, Quảng Bình",
      "country": "VN"
    },
    "about": [
      {
        "id": "accessibility",
        "name": "Accessibility",
        "options": [
          {
            "name": "Wheelchair accessible entrance",
            "enabled": true
          }
        ]
      }
    ],
    "userReviews": [
      {
        "name": "Jason Adams",
        "profilePicture": "https://lh3.googleusercontent.com/...",
        "rating": 5,
        "description": "Epic in all respects...",
        "When": "2025-6-1",
        "images": ["https://..."]
      }
    ]
  }'
```

## 2. Import Multiple Places (Batch)

```bash
curl -X POST http://localhost:8080/api/places/import/batch \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d @place.json
```

## 3. Get Place by ID

```bash
curl -X GET http://localhost:8080/api/places/550e8400-e29b-41d4-a716-446655440000 \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

## 4. Get Place by Google Place ID

```bash
curl -X GET "http://localhost:8080/api/places/google/ChIJI-LkoRfIODERrkzpzgMF7pI" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

## 5. Search Places by Location

```bash
# Search places within 10km radius of Hang Sơn Đoòng
curl -X GET "http://localhost:8080/api/places/search?latitude=17.464792&longitude=106.287394&radius=10&page=0&size=20" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

## 6. Search Places by Location and Category

```bash
# Search tourist attractions within 50km
curl -X GET "http://localhost:8080/api/places/search?latitude=17.464792&longitude=106.287394&radius=50&category=Tourist%20attraction&page=0&size=20" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

## 7. Get Place Reviews

```bash
curl -X GET http://localhost:8080/api/places/550e8400-e29b-41d4-a716-446655440000/reviews \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

## 8. Delete Place

```bash
curl -X DELETE http://localhost:8080/api/places/550e8400-e29b-41d4-a716-446655440000 \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

---

## Import from place.json file

```bash
# Convert place.json array to batch import format
curl -X POST http://localhost:8080/api/places/import/batch \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d @goroute_fe/place.json
```

---

## Response Examples

### Place Response
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "placeId": "ChIJI-LkoRfIODERrkzpzgMF7pI",
  "title": "Hang Sơn Đoòng",
  "category": "Tourist attraction",
  "address": "Bố Trạch, Quảng Bình, Vietnam",
  "latitude": 17.464792,
  "longitude": 106.287394,
  "phone": "",
  "website": "https://sondoongcave.info/",
  "googleMapsLink": "https://www.google.com/maps/place/...",
  "reviewCount": 1131,
  "reviewRating": 4.7,
  "reviewsPerRating": {
    "1": 53,
    "2": 12,
    "3": 30,
    "4": 84,
    "5": 952
  },
  "thumbnail": "https://...",
  "images": [...],
  "descriptions": "This enormous mountain cavern...",
  "priceRange": "",
  "openHours": {},
  "popularTimes": {},
  "about": [...],
  "createdAt": "2025-04-29T10:00:00",
  "updatedAt": "2025-04-29T10:00:00"
}
```

### Review Response
```json
{
  "id": "660e8400-e29b-41d4-a716-446655440000",
  "placeId": "550e8400-e29b-41d4-a716-446655440000",
  "reviewerName": "Jason Adams",
  "profilePicture": "https://...",
  "rating": 5,
  "description": "Epic in all respects...",
  "reviewDate": "2025-06-01",
  "images": ["https://..."]
}
```

---

## Integration with Activities

### Add Place to Trip Activity

```bash
curl -X POST http://localhost:8080/api/activities \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "tripId": "trip-uuid",
    "dayNumber": 1,
    "placeId": "ChIJI-LkoRfIODERrkzpzgMF7pI",
    "name": "Hang Sơn Đoòng",
    "startTime": "09:00",
    "endTime": "17:00",
    "notes": "Book tour in advance"
  }'
```

### Get Activities with Place Details

```bash
curl -X GET "http://localhost:8080/api/activities?tripId=trip-uuid&dayNumber=1" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

Response will include place details joined from places table.

---

## Notes

1. **Authentication**: All endpoints require JWT token in Authorization header
2. **Batch Import**: Use `place.json` file directly for batch import
3. **Radius**: In kilometers for search queries
4. **Pagination**: Use `page` and `size` parameters (default: page=0, size=20)
5. **Category Filter**: Optional, filters by exact category match
6. **Reviews**: Automatically imported when importing place with `userReviews` field
