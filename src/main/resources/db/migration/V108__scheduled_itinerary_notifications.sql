CREATE TABLE scheduled_notification_deliveries (
    id UUID PRIMARY KEY,
    recipient_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    trip_id UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    event_key VARCHAR(255) NOT NULL,
    scheduled_for TIMESTAMP NOT NULL,
    delivered_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_scheduled_notification_delivery UNIQUE (recipient_id, event_key)
);

CREATE INDEX idx_scheduled_notification_deliveries_trip
    ON scheduled_notification_deliveries(trip_id, scheduled_for);
