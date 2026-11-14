CREATE TRIGGER cmms_summary_epoch_insert BEFORE INSERT ON asset_summary_epoch FOR EACH ROW BEGIN
    DECLARE allocated_epoch BIGINT;
    INSERT INTO asset_summary_epoch_counter(asset_id,epoch) VALUES(NEW.asset_id,1)
      ON DUPLICATE KEY UPDATE epoch=epoch+1;
    SELECT epoch INTO allocated_epoch FROM asset_summary_epoch_counter WHERE asset_id=NEW.asset_id;
    SET NEW.epoch=allocated_epoch;
END
-- end-trigger
CREATE TRIGGER cmms_summary_epoch_update BEFORE UPDATE ON asset_summary_epoch FOR EACH ROW BEGIN
    DECLARE allocated_epoch BIGINT;
    INSERT INTO asset_summary_epoch_counter(asset_id,epoch) VALUES(NEW.asset_id,1)
      ON DUPLICATE KEY UPDATE epoch=epoch+1;
    SELECT epoch INTO allocated_epoch FROM asset_summary_epoch_counter WHERE asset_id=NEW.asset_id;
    SET NEW.epoch=allocated_epoch;
END
-- end-trigger
-- Invalidate values cached before this additive migration.
UPDATE asset_summary_epoch SET epoch=epoch+1
-- end-trigger
