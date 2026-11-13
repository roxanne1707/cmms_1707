CREATE TABLE maintenance_sla_policy (
 company_id bigint NOT NULL REFERENCES company(id) ON DELETE CASCADE,
 priority integer NOT NULL CHECK(priority BETWEEN 0 AND 3),
 response_minutes integer NOT NULL CHECK(response_minutes BETWEEN 1 AND 525600),
 repair_minutes integer NOT NULL CHECK(repair_minutes BETWEEN response_minutes AND 525600),
 enabled boolean NOT NULL DEFAULT true,
 effective_from timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_by bigint NOT NULL,
 PRIMARY KEY(company_id,priority)
);
CREATE TABLE maintenance_sla_case (
 work_order_id bigint PRIMARY KEY REFERENCES work_order(id) ON DELETE CASCADE,
 company_id bigint NOT NULL REFERENCES company(id) ON DELETE CASCADE,
 priority integer NOT NULL CHECK(priority BETWEEN 0 AND 3),
 started_at timestamptz NOT NULL,
 response_due_at timestamptz NOT NULL,
 repair_due_at timestamptz NOT NULL,
 responded_at timestamptz,
 completed_at timestamptz,
 response_event_id uuid,
 repair_event_id uuid,
 enrolled_by bigint,
 enrollment_reason varchar(500) NOT NULL,
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK(response_due_at >= started_at AND repair_due_at >= response_due_at)
);
CREATE INDEX ix_sla_company_due ON maintenance_sla_case(company_id,repair_due_at,work_order_id);
CREATE INDEX ix_sla_unprocessed_response ON maintenance_sla_case(response_due_at) WHERE response_event_id IS NULL;
CREATE INDEX ix_sla_unprocessed_repair ON maintenance_sla_case(repair_due_at) WHERE repair_event_id IS NULL;
CREATE TABLE maintenance_sla_escalation (
 company_id bigint NOT NULL REFERENCES company(id) ON DELETE CASCADE,
 work_order_id bigint NOT NULL REFERENCES work_order(id) ON DELETE CASCADE,
 kind varchar(20) NOT NULL CHECK(kind IN ('RESPONSE','REPAIR')),
 event_id uuid NOT NULL UNIQUE,
 occurred_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(work_order_id,kind)
);
-- Existing CMMS date columns are UTC timestamps without timezone; snapshot explicitly into timestamptz.
-- Priority ordinal 0..3 = NONE,LOW,MEDIUM,HIGH; Status.COMPLETE = 3 (existing enum storage).
CREATE FUNCTION cmms_capture_work_order_sla() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='INSERT' AND NOT COALESCE(NEW.is_demo,false) AND NOT COALESCE(NEW.archived,false) AND NEW.status<>3 THEN
  INSERT INTO maintenance_sla_case(work_order_id,company_id,priority,started_at,response_due_at,repair_due_at,enrolled_by,enrollment_reason)
  SELECT NEW.id,NEW.company_id,NEW.priority,NEW.created_at AT TIME ZONE 'UTC',
         (NEW.created_at AT TIME ZONE 'UTC')+make_interval(mins=>p.response_minutes),
         (NEW.created_at AT TIME ZONE 'UTC')+make_interval(mins=>p.repair_minutes),NEW.created_by,'POLICY_AT_CREATION'
  FROM maintenance_sla_policy p WHERE p.company_id=NEW.company_id AND p.priority=NEW.priority AND p.enabled
       AND (NEW.created_at AT TIME ZONE 'UTC')>=p.effective_from
  ON CONFLICT(work_order_id) DO NOTHING;
 END IF;
 UPDATE maintenance_sla_case SET
   responded_at=COALESCE(responded_at,CASE WHEN NEW.first_time_to_react>=NEW.created_at THEN NEW.first_time_to_react AT TIME ZONE 'UTC' ELSE NULL END),
   completed_at=COALESCE(completed_at,CASE WHEN NEW.status=3 AND NEW.completed_on>=NEW.created_at THEN NEW.completed_on AT TIME ZONE 'UTC' ELSE NULL END)
 WHERE work_order_id=NEW.id AND company_id=NEW.company_id;
 RETURN NEW;
END $$;
CREATE TRIGGER cmms_work_order_sla AFTER INSERT OR UPDATE OF first_time_to_react,completed_on,status ON work_order
 FOR EACH ROW EXECUTE FUNCTION cmms_capture_work_order_sla();
