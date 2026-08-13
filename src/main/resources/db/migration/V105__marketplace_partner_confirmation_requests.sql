-- Request-to-book is deliberately independent from payment. Existing payment
-- holds are migrated to partner-confirmation requests and retain their
-- allocated capacity until the response deadline.
ALTER TABLE hotel_bookings DROP CONSTRAINT IF EXISTS chk_hotel_booking_status;
ALTER TABLE activity_orders DROP CONSTRAINT IF EXISTS chk_activity_order_status;
ALTER TABLE hotel_bookings DROP CONSTRAINT IF EXISTS chk_hotel_payment_status;
ALTER TABLE activity_orders DROP CONSTRAINT IF EXISTS chk_activity_order_payment_status;
UPDATE hotel_bookings SET booking_status='PENDING_PARTNER_CONFIRMATION', payment_status='NOT_COLLECTED'
 WHERE booking_status='PENDING_PAYMENT';
UPDATE activity_orders SET order_status='PENDING_PARTNER_CONFIRMATION', payment_status='NOT_COLLECTED'
 WHERE order_status='PENDING_PAYMENT';
ALTER TABLE hotel_bookings ADD CONSTRAINT chk_hotel_booking_status CHECK (booking_status IN (
  'PENDING_PARTNER_CONFIRMATION','CONFIRMED','CHECKED_IN','COMPLETED','EXPIRED','FAILED',
  'CANCELLED_BY_GUEST','CANCELLED_BY_HOST','CANCELLED_BY_PLATFORM','NO_SHOW'
));
ALTER TABLE activity_orders ADD CONSTRAINT chk_activity_order_status CHECK (order_status IN (
  'PENDING_PARTNER_CONFIRMATION','CONFIRMED','CHECKED_IN','COMPLETED','EXPIRED','FAILED',
  'CANCELLED_BY_GUEST','CANCELLED_BY_HOST','CANCELLED_BY_PLATFORM','NO_SHOW'
));
ALTER TABLE hotel_bookings ADD CONSTRAINT chk_hotel_payment_status CHECK (payment_status IN (
  'NOT_COLLECTED','UNPAID','AUTHORIZED','PAID','PARTIALLY_REFUNDED','REFUNDED','FAILED','CHARGEBACK'
));
ALTER TABLE activity_orders ADD CONSTRAINT chk_activity_order_payment_status CHECK (payment_status IN (
  'NOT_COLLECTED','UNPAID','AUTHORIZED','PAID','PARTIALLY_REFUNDED','REFUNDED','FAILED','CHARGEBACK'
));
DROP INDEX IF EXISTS idx_hotel_bookings_pending_hold;
DROP INDEX IF EXISTS idx_activity_orders_pending_hold;
CREATE INDEX IF NOT EXISTS idx_hotel_bookings_pending_confirmation
 ON hotel_bookings(hold_expires_at) WHERE booking_status='PENDING_PARTNER_CONFIRMATION';
CREATE INDEX IF NOT EXISTS idx_activity_orders_pending_confirmation
 ON activity_orders(hold_expires_at) WHERE order_status='PENDING_PARTNER_CONFIRMATION';
