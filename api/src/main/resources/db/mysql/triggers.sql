CREATE TRIGGER cmms_asset_epoch_insert AFTER INSERT ON asset FOR EACH ROW BEGIN

    INSERT INTO asset_summary_epoch(company_id,asset_id,epoch) VALUES(NEW.company_id,NEW.id,1)
    ON DUPLICATE KEY UPDATE epoch=epoch+1;

END

-- end-trigger

CREATE TRIGGER cmms_asset_epoch_update AFTER UPDATE ON asset FOR EACH ROW BEGIN

    IF NOT (OLD.company_id <=> NEW.company_id) THEN
      DELETE FROM asset_summary_epoch WHERE company_id=OLD.company_id AND asset_id=OLD.id;

    INSERT INTO asset_summary_epoch(company_id,asset_id,epoch) VALUES(NEW.company_id,NEW.id,1)
    ON DUPLICATE KEY UPDATE epoch=epoch+1;

    END IF;
END

-- end-trigger

CREATE TRIGGER cmms_work_order_epoch_insert AFTER INSERT ON work_order FOR EACH ROW BEGIN
UPDATE asset_summary_epoch SET epoch=epoch+1 WHERE company_id=NEW.company_id AND asset_id=NEW.asset_id;
END

-- end-trigger

CREATE TRIGGER cmms_work_order_epoch_update AFTER UPDATE ON work_order FOR EACH ROW BEGIN

 IF NOT (OLD.company_id <=> NEW.company_id AND OLD.asset_id <=> NEW.asset_id
    AND OLD.created_at <=> NEW.created_at AND OLD.completed_on <=> NEW.completed_on
    AND OLD.status <=> NEW.status AND OLD.archived <=> NEW.archived AND OLD.is_demo <=> NEW.is_demo) THEN
   IF OLD.company_id < NEW.company_id OR (OLD.company_id=NEW.company_id AND OLD.asset_id < NEW.asset_id) THEN
     UPDATE asset_summary_epoch SET epoch=epoch+1 WHERE company_id=OLD.company_id AND asset_id=OLD.asset_id;UPDATE asset_summary_epoch SET epoch=epoch+1 WHERE company_id=NEW.company_id AND asset_id=NEW.asset_id;
   ELSE
     UPDATE asset_summary_epoch SET epoch=epoch+1 WHERE company_id=NEW.company_id AND asset_id=NEW.asset_id;
     IF NOT (OLD.company_id <=> NEW.company_id AND OLD.asset_id <=> NEW.asset_id) THEN UPDATE asset_summary_epoch SET epoch=epoch+1 WHERE company_id=OLD.company_id AND asset_id=OLD.asset_id; END IF;
   END IF;
 END IF;
END

-- end-trigger

CREATE TRIGGER cmms_work_order_epoch_delete AFTER DELETE ON work_order FOR EACH ROW BEGIN
UPDATE asset_summary_epoch SET epoch=epoch+1 WHERE company_id=OLD.company_id AND asset_id=OLD.asset_id;
END

-- end-trigger

CREATE TRIGGER cmms_work_order_sla_insert AFTER INSERT ON work_order FOR EACH ROW BEGIN

 IF NOT COALESCE(NEW.is_demo,false) AND NOT COALESCE(NEW.archived,false) AND NEW.status<>3 THEN
  INSERT INTO maintenance_sla_case(work_order_id,company_id,priority,started_at,response_due_at,repair_due_at,enrolled_by,enrollment_reason)
  SELECT NEW.id,NEW.company_id,NEW.priority,NEW.created_at,
    TIMESTAMPADD(MINUTE,p.response_minutes,NEW.created_at),TIMESTAMPADD(MINUTE,p.repair_minutes,NEW.created_at),NEW.created_by,'POLICY_AT_CREATION'
  FROM maintenance_sla_policy p WHERE p.company_id=NEW.company_id AND p.priority=NEW.priority AND p.enabled AND NEW.created_at>=p.effective_from;
 END IF;

 UPDATE maintenance_sla_case SET
  responded_at=COALESCE(responded_at,CASE WHEN NEW.first_time_to_react>=NEW.created_at THEN NEW.first_time_to_react ELSE NULL END),
  completed_at=COALESCE(completed_at,CASE WHEN NEW.status=3 AND NEW.completed_on>=NEW.created_at THEN NEW.completed_on ELSE NULL END)
 WHERE work_order_id=NEW.id AND company_id=NEW.company_id;

END

-- end-trigger

CREATE TRIGGER cmms_work_order_sla_update AFTER UPDATE ON work_order FOR EACH ROW BEGIN

 UPDATE maintenance_sla_case SET
  responded_at=COALESCE(responded_at,CASE WHEN NEW.first_time_to_react>=NEW.created_at THEN NEW.first_time_to_react ELSE NULL END),
  completed_at=COALESCE(completed_at,CASE WHEN NEW.status=3 AND NEW.completed_on>=NEW.created_at THEN NEW.completed_on ELSE NULL END)
 WHERE work_order_id=NEW.id AND company_id=NEW.company_id;

END

-- end-trigger

CREATE TRIGGER asset_no_cycle_insert BEFORE INSERT ON asset FOR EACH ROW BEGIN

 DECLARE ancestor BIGINT;
 DECLARE depth INT DEFAULT 0;
 DECLARE ancestor_company BIGINT;
 IF NEW.parent_asset_id IS NOT NULL THEN
  INSERT INTO cmms_transaction_lock(lock_key) VALUES(CONCAT('hierarchy:asset:',NEW.company_id)) ON DUPLICATE KEY UPDATE lock_key=lock_key;
  SET ancestor=NEW.parent_asset_id;
  WHILE ancestor IS NOT NULL DO
   IF ancestor=NEW.id OR depth>=10000 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Circular or excessive-depth hierarchy'; END IF;
   SELECT company_id,parent_asset_id INTO ancestor_company,ancestor FROM asset WHERE id=ancestor;
   IF NOT (ancestor_company <=> NEW.company_id) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Cross-company hierarchy is forbidden'; END IF;
   SET depth=depth+1;
  END WHILE;
 END IF;

END

-- end-trigger

CREATE TRIGGER asset_no_cycle_update BEFORE UPDATE ON asset FOR EACH ROW BEGIN

 DECLARE ancestor BIGINT;
 DECLARE depth INT DEFAULT 0;
 DECLARE ancestor_company BIGINT;
 IF NEW.parent_asset_id IS NOT NULL AND (NOT (OLD.parent_asset_id <=> NEW.parent_asset_id) OR NOT (OLD.company_id <=> NEW.company_id)) THEN
  INSERT INTO cmms_transaction_lock(lock_key) VALUES(CONCAT('hierarchy:asset:',NEW.company_id)) ON DUPLICATE KEY UPDATE lock_key=lock_key;
  SET ancestor=NEW.parent_asset_id;
  WHILE ancestor IS NOT NULL DO
   IF ancestor=NEW.id OR depth>=10000 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Circular or excessive-depth hierarchy'; END IF;
   SELECT company_id,parent_asset_id INTO ancestor_company,ancestor FROM asset WHERE id=ancestor;
   IF NOT (ancestor_company <=> NEW.company_id) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Cross-company hierarchy is forbidden'; END IF;
   SET depth=depth+1;
  END WHILE;
 END IF;

END

-- end-trigger

CREATE TRIGGER location_no_cycle_insert BEFORE INSERT ON location FOR EACH ROW BEGIN

 DECLARE ancestor BIGINT;
 DECLARE depth INT DEFAULT 0;
 DECLARE ancestor_company BIGINT;
 IF NEW.parent_location_id IS NOT NULL THEN
  INSERT INTO cmms_transaction_lock(lock_key) VALUES(CONCAT('hierarchy:location:',NEW.company_id)) ON DUPLICATE KEY UPDATE lock_key=lock_key;
  SET ancestor=NEW.parent_location_id;
  WHILE ancestor IS NOT NULL DO
   IF ancestor=NEW.id OR depth>=10000 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Circular or excessive-depth hierarchy'; END IF;
   SELECT company_id,parent_location_id INTO ancestor_company,ancestor FROM location WHERE id=ancestor;
   IF NOT (ancestor_company <=> NEW.company_id) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Cross-company hierarchy is forbidden'; END IF;
   SET depth=depth+1;
  END WHILE;
 END IF;

END

-- end-trigger

CREATE TRIGGER location_no_cycle_update BEFORE UPDATE ON location FOR EACH ROW BEGIN

 DECLARE ancestor BIGINT;
 DECLARE depth INT DEFAULT 0;
 DECLARE ancestor_company BIGINT;
 IF NEW.parent_location_id IS NOT NULL AND (NOT (OLD.parent_location_id <=> NEW.parent_location_id) OR NOT (OLD.company_id <=> NEW.company_id)) THEN
  INSERT INTO cmms_transaction_lock(lock_key) VALUES(CONCAT('hierarchy:location:',NEW.company_id)) ON DUPLICATE KEY UPDATE lock_key=lock_key;
  SET ancestor=NEW.parent_location_id;
  WHILE ancestor IS NOT NULL DO
   IF ancestor=NEW.id OR depth>=10000 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Circular or excessive-depth hierarchy'; END IF;
   SELECT company_id,parent_location_id INTO ancestor_company,ancestor FROM location WHERE id=ancestor;
   IF NOT (ancestor_company <=> NEW.company_id) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Cross-company hierarchy is forbidden'; END IF;
   SET depth=depth+1;
  END WHILE;
 END IF;

END
-- end-trigger
