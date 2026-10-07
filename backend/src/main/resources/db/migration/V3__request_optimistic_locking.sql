-- Existing requests start at version zero; Hibernate increments it on updates.
ALTER TABLE delivery_requests ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
