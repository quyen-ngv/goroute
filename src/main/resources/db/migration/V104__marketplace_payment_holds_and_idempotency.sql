-- Prevent abandoned payment sessions from permanently consuming inventory and
-- make client retries safe for marketplace booking/order creation.
ALTER TABLE hotel_bookings
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(120),
    ADD COLUMN IF NOT EXISTS hold_expires_at TIMESTAMP;
CREATE UNIQUE INDEX IF NOT EXISTS uq_hotel_bookings_user_idempotency
    ON hotel_bookings(user_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_hotel_bookings_pending_hold
    ON hotel_bookings(hold_expires_at) WHERE booking_status = 'PENDING_PAYMENT';

ALTER TABLE activity_orders
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(120),
    ADD COLUMN IF NOT EXISTS hold_expires_at TIMESTAMP;
CREATE UNIQUE INDEX IF NOT EXISTS uq_activity_orders_user_idempotency
    ON activity_orders(user_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_activity_orders_pending_hold
    ON activity_orders(hold_expires_at) WHERE order_status = 'PENDING_PAYMENT';
