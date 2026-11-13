-- Keep the historical request.work_order_id FK; only its obsolete one-to-one uniqueness is removed.
DO $$
DECLARE constraint_row record;
BEGIN
  FOR constraint_row IN
    SELECT c.conname FROM pg_constraint c JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attname='work_order_id'
    WHERE c.conrelid='request'::regclass AND c.contype='u' AND c.conkey=ARRAY[a.attnum]::smallint[]
  LOOP
    EXECUTE format('ALTER TABLE request DROP CONSTRAINT %I',constraint_row.conname);
  END LOOP;
END $$;
CREATE INDEX IF NOT EXISTS ix_request_company_work_order ON request(company_id,work_order_id);
CREATE TABLE request_link_audit (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 request_id bigint NOT NULL REFERENCES request(id) ON DELETE CASCADE,
 company_id bigint NOT NULL REFERENCES company(id) ON DELETE CASCADE,
 work_order_id bigint NOT NULL,
 actor_id bigint NOT NULL,
 reason varchar(500) NOT NULL CHECK (char_length(trim(reason)) BETWEEN 5 AND 500),
 linked_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(request_id,work_order_id)
);
CREATE INDEX ix_request_link_company ON request_link_audit(company_id,linked_at DESC);
