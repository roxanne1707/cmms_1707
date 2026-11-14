-- A counter is intentionally independent of asset lifetime, like the PostgreSQL sequence.
-- Existing business/summary foreign keys and triggers are preserved.
CREATE TABLE asset_summary_epoch_counter (
 asset_id BIGINT PRIMARY KEY,
 epoch BIGINT NOT NULL,
 CONSTRAINT ck_asset_epoch_counter_positive CHECK (epoch > 0)
) ENGINE=InnoDB;
INSERT INTO asset_summary_epoch_counter(asset_id,epoch)
 SELECT asset_id,MAX(epoch) FROM asset_summary_epoch GROUP BY asset_id;
