# Admin Push Notification API

## Overview
API cho phép gửi push notification đến 1 hoặc nhiều users cùng lúc bằng email.

## Endpoint
```
POST /v1/api/notifications/admin/push
```

## Authentication
Requires valid JWT token in Authorization header.

## Request Body
```json
{
  "emails": ["user1@example.com", "user2@example.com"],
  "title": "System Maintenance Notice",
  "body": "The app will be under maintenance from 2AM to 4AM on Sunday",
  "deepLink": "/notifications",
  "data": {
    "type": "maintenance",
    "startTime": "2026-07-12T02:00:00Z",
    "endTime": "2026-07-12T04:00:00Z"
  },
  "imageUrl": "https://example.com/maintenance.png",
  "priority": "high"
}
```

### Fields
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `emails` | string[] | ✅ | List of recipient emails (1 or more) |
| `title` | string | ✅ | Notification title (max 100 chars) |
| `body` | string | ✅ | Notification body/brief (max 300 chars) |
| `deepLink` | string | ❌ | Deep link for redirect (e.g., "/trips/{tripId}") |
| `data` | object | ❌ | Custom data object |
| `imageUrl` | string | ❌ | Image URL for rich notification |
| `priority` | string | ❌ | "high" or "normal" (default: high) |

## Response
```json
{
  "success": true,
  "data": {
    "totalRequested": 3,
    "successCount": 2,
    "notFoundCount": 1,
    "notFoundEmails": ["notfound@example.com"],
    "noDeviceCount": 0,
    "noDeviceEmails": [],
    "message": "Sent 2/3 notifications successfully"
  }
}
```

### Response Fields
| Field | Description |
|-------|-------------|
| `totalRequested` | Total emails in request |
| `successCount` | Number of notifications sent successfully |
| `notFoundCount` | Number of users not found |
| `notFoundEmails` | List of emails that don't exist |
| `noDeviceCount` | Number of users without active devices |
| `noDeviceEmails` | List of emails with no devices |
| `message` | Summary message |

## Example Curl
```bash
curl -X POST https://api.tripmind.com/v1/api/notifications/admin/push \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "emails": ["user1@example.com", "user2@example.com"],
    "title": "New Feature Released",
    "body": "Check out our new expense split calculator!",
    "deepLink": "/features/expense-split",
    "imageUrl": "https://tripmind.com/images/expense-split.png",
    "priority": "high"
  }'
```

## Use Cases

### 1. System Announcement
```json
{
  "emails": ["all-users@example.com"],
  "title": "TripMind Update",
  "body": "New version 2.0 is now available with exciting features!",
  "deepLink": "/updates"
}
```

### 2. Promotional Campaign
```json
{
  "emails": ["user1@example.com", "user2@example.com"],
  "title": "Special Offer",
  "body": "Get 20% off on Premium plan this month!",
  "deepLink": "/subscription/premium",
  "imageUrl": "https://tripmind.com/promo.png"
}
```

### 3. Critical Alert
```json
{
  "emails": ["user@example.com"],
  "title": "Security Alert",
  "body": "Unusual login detected from new device",
  "deepLink": "/security/sessions",
  "priority": "high"
}
```

## Implementation Details

### Backend Flow
1. Validate request (emails, title, body required)
2. For each email:
   - Find user by email
   - Check user has active devices
   - Create notification record in DB
   - Send FCM push to all user's devices
3. Return success/failure counts

### Database
- Notification saved with type: `ADMIN_ANNOUNCEMENT`
- No tripId for admin notifications
- No actorId for admin notifications

### Multi-language Support
Title and body from request are used AS-IS (không dùng template renderer).
User's device language setting will still apply to app UI but not the notification content.

## TODO: Add Admin Permission Check
Currently, any authenticated user can call this API. Add admin check:

```java
if (!userService.isAdmin(adminUserId)) {
    throw new UnauthorizedException("Only admins can send push notifications");
}
```

## Files Modified
- `NotificationType.java` - Added `ADMIN_ANNOUNCEMENT`, `ADMIN_MESSAGE`
- `AdminPushNotificationRequest.java` - Request DTO
- `AdminPushNotificationResponse.java` - Response DTO
- `NotificationController.java` - Added `/admin/push` endpoint
- `NotificationService.java` - Added `sendAdminPushNotification()` method
- `NotificationServiceImpl.java` - Implementation
- `NotificationTemplateRenderer.java` - Need to add admin templates (see below)

## Required Manual Edit
Add these entries to `NotificationTemplateRenderer.java`:

### Vietnamese (line ~123):
```java
entry(NotificationType.ADMIN_ANNOUNCEMENT, "Thông báo từ TripMind", "{body}"),
entry(NotificationType.ADMIN_MESSAGE, "Tin nhắn từ TripMind", "{body}")
```

### Japanese (line ~153):
```java
entry(NotificationType.ADMIN_ANNOUNCEMENT, "TripMindからのお知らせ", "{body}"),
entry(NotificationType.ADMIN_MESSAGE, "TripMindからのメッセージ", "{body}")
```

### Korean (line ~183):
```java
entry(NotificationType.ADMIN_ANNOUNCEMENT, "TripMind 공지사항", "{body}"),
entry(NotificationType.ADMIN_MESSAGE, "TripMind 메시지", "{body}")
```

Insert these BEFORE the closing `);` of each language method.
