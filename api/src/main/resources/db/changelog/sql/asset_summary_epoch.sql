-- Derived versions are incremented in the SAME transaction as every relevant row mutation.
CREATE SEQUENCE cmms_asset_summary_epoch_seq START WITH 1;
CREATE TABLE asset_summary_epoch (
    company_id bigint NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    asset_id bigint NOT NULL REFERENCES asset(id) ON DELETE CASCADE,
    epoch bigint NOT NULL DEFAULT nextval('cmms_asset_summary_epoch_seq') CHECK (epoch > 0),
    PRIMARY KEY (company_id, asset_id)
);

INSERT INTO asset_summary_epoch(company_id,asset_id,epoch)
SELECT company_id,id,nextval('cmms_asset_summary_epoch_seq') FROM asset;

CREATE FUNCTION cmms_initialize_asset_epoch() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='UPDATE' AND OLD.company_id IS DISTINCT FROM NEW.company_id THEN
        DELETE FROM asset_summary_epoch WHERE asset_id=OLD.id AND company_id=OLD.company_id;
    END IF;
    INSERT INTO asset_summary_epoch(company_id,asset_id,epoch) VALUES(NEW.company_id,NEW.id,nextval('cmms_asset_summary_epoch_seq'))
    ON CONFLICT(company_id,asset_id) DO NOTHING;
    RETURN NEW;
END $$;

CREATE TRIGGER cmms_asset_epoch AFTER INSERT OR UPDATE OF company_id ON asset
FOR EACH ROW EXECUTE FUNCTION cmms_initialize_asset_epoch();

CREATE FUNCTION cmms_touch_work_order_epoch() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    old_company bigint; old_asset bigint; new_company bigint; new_asset bigint; target record;
BEGIN
    IF TG_OP='UPDATE' AND
       ROW(OLD.company_id,OLD.asset_id,OLD.created_at,OLD.completed_on,OLD.status,OLD.archived,OLD.is_demo)
       IS NOT DISTINCT FROM
       ROW(NEW.company_id,NEW.asset_id,NEW.created_at,NEW.completed_on,NEW.status,NEW.archived,NEW.is_demo)
    THEN RETURN NEW; END IF;
    IF TG_OP<>'INSERT' THEN old_company:=OLD.company_id; old_asset:=OLD.asset_id; END IF;
    IF TG_OP<>'DELETE' THEN new_company:=NEW.company_id; new_asset:=NEW.asset_id; END IF;
    -- An asset move touches both sides in deterministic order. Deleted assets cascade their version rows.
    FOR target IN
        SELECT DISTINCT x.company_id,x.asset_id FROM
            (VALUES(old_company,old_asset),(new_company,new_asset)) x(company_id,asset_id)
        WHERE x.asset_id IS NOT NULL ORDER BY x.company_id,x.asset_id
    LOOP
        INSERT INTO asset_summary_epoch(company_id,asset_id,epoch)
        SELECT a.company_id,a.id,nextval('cmms_asset_summary_epoch_seq') FROM asset a
        WHERE a.id=target.asset_id AND a.company_id=target.company_id
        ON CONFLICT(company_id,asset_id) DO UPDATE
        SET epoch=nextval('cmms_asset_summary_epoch_seq');
    END LOOP;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER cmms_work_order_epoch AFTER INSERT OR UPDATE OR DELETE ON work_order
FOR EACH ROW EXECUTE FUNCTION cmms_touch_work_order_epoch();

-- TRUNCATE does not invoke row DELETE triggers; it invalidates all asset summaries explicitly.
CREATE FUNCTION cmms_truncate_work_order_epoch() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    UPDATE asset_summary_epoch SET epoch=nextval('cmms_asset_summary_epoch_seq');
    RETURN NULL;
END $$;
CREATE TRIGGER cmms_work_order_truncate_epoch AFTER TRUNCATE ON work_order
FOR EACH STATEMENT EXECUTE FUNCTION cmms_truncate_work_order_epoch();