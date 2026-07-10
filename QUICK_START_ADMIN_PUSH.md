# 🚀 Quick Start - Admin Push Notification

## 5-Minute Test Guide

### Step 1: Start Backend (if not running)
```bash
cd goroute
./mvnw spring-boot:run
```

### Step 2: Get JWT Token

#### Option A: Register New User
```bash
curl -X POST http://localhost:8080/v1/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "Test Admin",
    "email": "admin@tripmind.com",
    "password": "admin123"
  }'
```

Copy `accessToken` from response.

#### Option B: Login Existing User
```bash
curl -X POST http://localhost:8080/v1/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@tripmind.com",
    "password": "admin123"
  }'
```

### Step 3: Register a Device (Simulate Mobile App)
```bash
curl -X POST http://localhost:8080/v1/api/notifications/devices \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "fcmToken": "test_fcm_token_12345",
    "deviceType": "android",
    "deviceName": "Samsung Galaxy S24",
    "language": "vi"
  }'
```

### Step 4: Send Admin Push Notification 🎉
```bash
curl -X POST http://localhost:8080/v1/api/notifications/admin/push \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "emails": ["admin@tripmind.com"],
    "title": "Welcome to TripMind!",
    "body": "Your account is ready. Start planning amazing trips!",
    "deepLink": "/home",
    "priority": "high"
  }'
```

### Step 5: Verify Response
```json
{
  "success": true,
  "data": {
    "totalRequested": 1,
    "successCount": 1,
    "notFoundCount": 0,
    "notFoundEmails": [],
    "noDeviceCount": 0,
    "noDeviceEmails": [],
    "message": "Sent 1/1 notifications successfully"
  }
}
```

### Step 6: Check Database
```sql
-- Check notification created
SELECT * FROM notifications 
WHERE type = 'ADMIN_ANNOUNCEMENT' 
ORDER BY created_at DESC 
LIMIT 1;

-- Check user devices
SELECT u.email, ud.device_type, ud.language
FROM user_devices ud
JOIN users u ON u.id = ud.user_id
WHERE u.email = 'admin@tripmind.com';
```

## 📱 Common Test Scenarios

### Test 1: Multiple Users
```bash
curl -X POST http://localhost:8080/v1/api/notifications/admin/push \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "emails": ["user1@example.com", "user2@example.com", "user3@example.com"],
    "title": "Team Announcement",
    "body": "Monthly team meeting at 3PM today",
    "deepLink": "/calendar"
  }'
```

### Test 2: With Image & Custom Data
```bash
curl -X POST http://localhost:8080/v1/api/notifications/admin/push \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "emails": ["user@example.com"],
    "title": "New Feature: Expense Split",
    "body": "Split bills easily with your travel buddies!",
    "deepLink": "/features/expense-split",
    "imageUrl": "https://tripmind.com/expense-feature.png",
    "data": {
      "featureId": "expense-split",
      "category": "new-feature"
    }
  }'
```

### Test 3: Invalid Email (Error Handling)
```bash
curl -X POST http://localhost:8080/v1/api/notifications/admin/push \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "emails": ["valid@example.com", "notfound@example.com"],
    "title": "Test",
    "body": "Testing error handling"
  }'
```

Expected response:
```json
{
  "success": true,
  "data": {
    "totalRequested": 2,
    "successCount": 1,
    "notFoundCount": 1,
    "notFoundEmails": ["notfound@example.com"],
    "noDeviceCount": 0,
    "noDeviceEmails": [],
    "message": "Sent 1/2 notifications successfully"
  }
}
```

## 🎯 What to Check

### ✅ Success Indicators
1. Response status: 200 OK
2. `successCount` > 0
3. `notFoundEmails` array empty (or expected)
4. Database has new notification record
5. Firebase logs show FCM request (if real token)

### ❌ Common Errors

#### Error 1: 401 Unauthorized
```json
{"success": false, "error": "UNAUTHORIZED"}
```
**Fix:** Get new JWT token (Step 2)

#### Error 2: Validation Error
```json
{"success": false, "error": "INVALID_PARAMETERS"}
```
**Fix:** Check request body format

#### Error 3: Empty Response / No Success
**Fix:** Check backend logs for errors

## 🔧 Debugging Tips

### Check Backend Logs
```bash
# Look for these log messages
tail -f logs/application.log | grep "Admin push"
```

Expected logs:
```
INFO: Admin push notification request from userId: xxx, recipients: 1
INFO: Successfully sent admin push to: admin@tripmind.com
INFO: Admin push completed: success=1, notFound=0, noDevice=0
```

### Check FCM Delivery (if real token)
1. Go to Firebase Console
2. Cloud Messaging → View logs
3. Search for your FCM token
4. Check delivery status

### Verify Database
```sql
-- Latest notifications
SELECT 
  n.id,
  n.type,
  u.email,
  n.created_at,
  n.data
FROM notifications n
JOIN users u ON u.id = n.user_id
WHERE n.type = 'ADMIN_ANNOUNCEMENT'
ORDER BY n.created_at DESC
LIMIT 5;
```

## 📋 Postman Quick Import

1. Open Postman
2. Import → File → Select `ADMIN_PUSH_TEST_EXAMPLES.json`
3. Set Environment Variables:
   - `base_url` = `http://localhost:8080`
   - `jwt_token` = Your token from Step 2
4. Run any request from collection

## ⚡ Pro Tips

1. **Use Postman Environment**: Save time by storing token as variable
2. **Test with Self**: Use your own email first to verify
3. **Check Logs**: Backend logs show detailed execution flow
4. **Batch Test**: Test with 2-3 users to verify multi-user flow
5. **Invalid Email Test**: Always test error handling

## 🎉 Success!

If you see:
```json
{
  "success": true,
  "data": {
    "successCount": 1,
    "message": "Sent 1/1 notifications successfully"
  }
}
```

✅ **API is working perfectly!**

You can now:
- Send push to any registered users
- Use in production
- Integrate with admin dashboard
- Schedule notifications (future enhancement)

---

**Next Steps:**
1. Add admin permission check (when ready)
2. Integrate with admin UI
3. Add scheduling feature
4. Monitor delivery rates
