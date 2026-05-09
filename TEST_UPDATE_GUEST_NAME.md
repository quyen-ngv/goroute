# Test Case: Update Guest Name

## API Endpoint
```
PUT /v1/api/trips/{tripId}/members/{guestMemberId}/guest-name
```

## Request
```json
{
  "guestName": "Nguyễn Văn B"
}
```

## Test Steps

### 1. Create a trip
```bash
POST /v1/api/trips
Authorization: Bearer {owner_token}
{
  "name": "Test Trip",
  "destination": "Hanoi",
  "startDate": "2026-05-01",
  "endDate": "2026-05-05"
}
```

### 2. Add guest member
```bash
POST /v1/api/trips/{tripId}/members
Authorization: Bearer {owner_token}
{
  "isGuest": true,
  "guestName": "Nguyễn Văn A",
  "role": "MEMBER"
}
```
Response: Save `guestMemberId`

### 3. Update guest name
```bash
PUT /v1/api/trips/{tripId}/members/{guestMemberId}/guest-name
Authorization: Bearer {owner_token}
{
  "guestName": "Nguyễn Văn B"
}
```

### 4. Verify
```bash
GET /v1/api/trips/{tripId}/members
Authorization: Bearer {owner_token}
```
Expected: Guest member name should be "Nguyễn Văn B"

## Error Cases

### Case 1: Non-owner tries to update
```bash
PUT /v1/api/trips/{tripId}/members/{guestMemberId}/guest-name
Authorization: Bearer {non_owner_token}
```
Expected: 403 FORBIDDEN

### Case 2: Update non-guest member
```bash
PUT /v1/api/trips/{tripId}/members/{regularMemberId}/guest-name
```
Expected: 400 INVALID_PARAMETERS "Member is not a guest"

### Case 3: Empty guest name
```bash
PUT /v1/api/trips/{tripId}/members/{guestMemberId}/guest-name
{
  "guestName": ""
}
```
Expected: 400 Validation error

### Case 4: Guest member from different trip
```bash
PUT /v1/api/trips/{tripId}/members/{otherTripGuestMemberId}/guest-name
```
Expected: 403 FORBIDDEN "Guest member does not belong to this trip"

## Frontend Flow

1. User clicks on guest member in members list
2. Member details sheet opens
3. User sees edit icon (✏️) next to guest name
4. User clicks edit icon
5. Dialog opens with current name pre-filled
6. User enters new name
7. User clicks "Lưu"
8. API call is made
9. Success toast shows "Đã đổi tên thành {newName}"
10. Members list refreshes automatically
