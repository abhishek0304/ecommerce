CREATE INDEX ix_order_recovery ON purchase_orders (status, updated_at);
CREATE INDEX ix_order_customer_created ON purchase_orders (user_id, created_at);
CREATE INDEX ix_shipment_due ON order_shipments (status, mode, next_track_at);
