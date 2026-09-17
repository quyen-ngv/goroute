-- Guest-side cart for the marketplace.
--
-- A cart row is a *selection*, never a hold: no inventory is reserved here. Reserving on
-- "add to cart" would let one guest park a room for days by leaving a tab open, and the
-- hold-expiry job only knows about bookings. Inventory is still taken exactly once, in
-- createBooking / createOrder, which re-validate and re-price from the live calendar.
--
-- Consequence, and it is the honest one: a cart line can go stale. The read path re-quotes
-- every line against the calendar and marks it unavailable with a reason, so the guest sees
-- the truth before checkout rather than a failure after it.
--
-- One row per distinct selection per user. selection_hash is computed in the service from the
-- identifying fields of the line (dates, room, rate, units...), because a partial unique index
-- over a dozen nullable columns cannot express "same selection" across the two item types.
-- Re-adding the same selection updates the existing row instead of growing a duplicate.

CREATE TABLE IF NOT EXISTS marketplace_cart_items (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    item_type VARCHAR(16) NOT NULL,
    selection_hash VARCHAR(64) NOT NULL,

    -- HOTEL
    hotel_id UUID,
    room_type_id UUID,
    rate_plan_id UUID,
    check_in_date DATE,
    check_out_date DATE,
    quantity INTEGER,
    adults INTEGER,
    children INTEGER,

    -- ACTIVITY
    activity_id UUID,
    package_id UUID,
    slot_id UUID,
    unit_quantities JSONB,

    special_requests TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_cart_item_type CHECK (item_type IN ('HOTEL', 'ACTIVITY')),
    -- Each type carries its own key columns; neither may borrow the other's.
    CONSTRAINT chk_cart_item_shape CHECK (
        (item_type = 'HOTEL'
            AND hotel_id IS NOT NULL AND room_type_id IS NOT NULL AND rate_plan_id IS NOT NULL
            AND check_in_date IS NOT NULL AND check_out_date IS NOT NULL
            AND quantity IS NOT NULL AND adults IS NOT NULL AND children IS NOT NULL
            AND activity_id IS NULL AND package_id IS NULL AND slot_id IS NULL)
        OR
        (item_type = 'ACTIVITY'
            AND activity_id IS NOT NULL AND package_id IS NOT NULL AND slot_id IS NOT NULL
            AND hotel_id IS NULL AND room_type_id IS NULL AND rate_plan_id IS NULL
            AND check_in_date IS NULL AND check_out_date IS NULL)
    ),
    CONSTRAINT chk_cart_stay_order CHECK (check_out_date IS NULL OR check_out_date > check_in_date),
    CONSTRAINT chk_cart_party CHECK (
        (quantity IS NULL OR quantity >= 1)
        AND (adults IS NULL OR adults >= 1)
        AND (children IS NULL OR children >= 0)
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_cart_user_selection
    ON marketplace_cart_items(user_id, selection_hash);

-- The list query: everything for one user, newest first.
CREATE INDEX IF NOT EXISTS idx_cart_user_created
    ON marketplace_cart_items(user_id, created_at DESC);
