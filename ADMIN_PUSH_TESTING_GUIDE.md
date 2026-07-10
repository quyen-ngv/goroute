# Testing Guide - Admin Push Notification API

## Prerequisites

1. Backend server running on `http://localhost:8080`
2. Valid JWT token (login as admin user)
3. At least one user with registered device (FCM token)
4. Postman or curl for testing

## Setup Steps

### 1. Create Test User & Register Device

```bash
# Register a test user
curl -X POST http://localhost:8080/v1/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "Test User",
    "email": "testuser@example.com",
    "password": "test123"
  }'

# Response will include JWT token
```

### 2. Register Device (from mobile app or simulate)

```bash
curl -X POST http://localhost:8080/v1/api/notifications/devices \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "fcmToken": "fake_fcm_token_for_testing",
    "deviceType": "android",
    "deviceName": "Test Device",
    "language": "vi"
  }'
```

### 3. Test Admin Push Notification

```bash
curl -X POST http://localhost:8080/v1/api/notifications/admin/push \
  -H "Authorization: Bearer YOUR_ADMIN_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "emails": ["testuser@example.com"],
    "title": "Test Notification",
    "body": "This is a test push notification",
    "deepLink": "/home",
    "priority": "high"
  }'
```

## Expected Responses

### Success Response (All users found)
```json
{
  "success": true,
  "data": {
    "totalRequested": 2,
    "successCount": 2,
    "notFoundCount": 0,
    "notFoundEmails": [],
    "noDeviceCount": 0,
    "noDeviceEmails": [],
    "message": "Sent 2/2 notifications successfully"
  }
}
```

### Partial Success (Some users not found)
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

### User Without Devices
```json
{
  "success": true,
  "data": {
    "totalRequested": 2,
    "successCount": 1,
    "notFoundCount": 0,
    "notFoundEmails": [],
    "noDeviceCount": 1,
    "noDeviceEmails": ["user-no-device@example.com"],
    "message": "Sent 1/2 notifications successfully"
  }
}
```

## Validation Errors

### Missing Required Fields
```json
{
  "success": false,
  "error": "INVALID_PARAMETERS",
  "message": "At least one email is required"
}
```

### Invalid Email Format
```json
{
  "success": false,
  "error": "INVALID_PARAMETERS",
  "message": "Invalid email format"
}
```

### Unauthorized
```json
{
  "success": false,
  "error": "UNAUTHORIZED",
  "message": "Invalid or expired token"
}
```

## Test Scenarios

### ✅ Scenario 1: Single User Push
**Input:**
- 1 valid email
- Title & body
- Deep link

**Expected:**
- Push sent successfully
- Notification appears in user's app
- Tapping opens the specified deep link

### ✅ Scenario 2: Multiple Users Push
**Input:**
- 3 valid emails
- Title, body, image
- Custom data

**Expected:**
- All 3 users receive notification
- Image displayed in notification
- Custom data accessible in app

### ✅ Scenario 3: Mix Valid & Invalid Emails
**Input:**
- 2 valid emails
- 1 non-existent email

**Expected:**
- Response shows: successCount=2, notFoundCount=1
- notFoundEmails array contains the invalid email
- Valid users receive notification

### ✅ Scenario 4: User Without Devices
**Input:**
- User exists but has no registered devices

**Expected:**
- Response shows: noDeviceCount=1
- noDeviceEmails contains the user's email
- No FCM push sent

### ✅ Scenario 5: Multi-language Support
**Input:**
- Users with different device languages (vi, en, ja, ko)

**Expected:**
- Notification template rendered in user's language
- Title from request used as-is
- Body from request used as-is

## Database Verification

### Check Notification Record
```sql
SELECT * FROM notifications 
WHERE type = 'ADMIN_ANNOUNCEMENT' 
ORDER BY created_at DESC 
LIMIT 10;
```

### Check User Devices
```sql
SELECT u.email, ud.device_type, ud.language, ud.is_active
FROM user_devices ud
JOIN users u ON u.id = ud.user_id
WHERE u.email IN ('testuser@example.com');
```

### Check Notification Count
```sql
SELECT type, COUNT(*) as count
FROM notifications
WHERE type IN ('ADMIN_ANNOUNCEMENT', 'ADMIN_MESSAGE')
GROUP BY type;
```

## Debugging Tips

### 1. Check Logs
```bash
# Look for these log messages
grep "Admin push notification request" logs/application.log
grep "Successfully sent admin push to" logs/application.log
grep "Failed to send admin push to" logs/application.log
```

### 2. Firebase Issues
- Check FCM token validity
- Verify Firebase credentials configured
- Check if device deleted invalid tokens

### 3. User Not Receiving Push
- Verify user has active devices: `is_active = true`
- Check FCM token not expired
- Verify app has notification permission
- Check device language setting

## Load Testing

### Test with 100 Users
```bash
# Generate list of 100 test emails
emails='['
for i in {1..100}; do
  emails+="\"testuser${i}@example.com\","
done
emails="${emails%,}]"

curl -X POST http://localhost:8080/v1/api/notifications/admin/push \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"emails\": $emails,
    \"title\": \"Load Test\",
    \"body\": \"Testing 100 users\",
    \"priority\": \"normal\"
  }"
```

## Performance Metrics

Expected performance:
- **Single user**: < 500ms
- **10 users**: < 2s
- **100 users**: < 10s
- **1000 users**: Consider batch processing

## Security Checklist

- [ ] Admin authorization implemented
- [ ] Rate limiting configured
- [ ] Input validation (email, title, body length)
- [ ] SQL injection prevented (using parameterized queries)
- [ ] XSS prevention in notification content
- [ ] Audit log for admin actions

## Next Steps

1. Add admin permission check to controller
2. Implement rate limiting (max 1000 emails per request)
3. Add async processing for large batches
4. Create admin dashboard for sending notifications
5. Add notification scheduling feature
