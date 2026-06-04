-- Reset legacy rendered notification rows before switching in-app rendering to type + data.
TRUNCATE TABLE notifications;

-- In-app notifications are rendered on the client from type + data.
ALTER TABLE notifications
    ALTER COLUMN title DROP NOT NULL,
    ALTER COLUMN body DROP NOT NULL;

-- Push language is resolved per registered device.
ALTER TABLE user_devices
    ADD COLUMN IF NOT EXISTS language VARCHAR(10) NOT NULL DEFAULT 'en';
