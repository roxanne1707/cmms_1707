CREATE TABLE event_report_attachment (
 company_id BIGINT NOT NULL REFERENCES company(id) ON DELETE CASCADE,
 event_id UUID NOT NULL,
 filename VARCHAR(180) NOT NULL,
 content BYTEA NOT NULL CHECK (octet_length(content) BETWEEN 1 AND 10485760),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(company_id,event_id)
);
CREATE TABLE maintenance_schedule_execution (
 company_id BIGINT NOT NULL REFERENCES company(id) ON DELETE CASCADE,
 schedule_id BIGINT NOT NULL,
 execution_kind VARCHAR(20) NOT NULL CHECK(execution_kind IN ('CREATE','REMINDER')),
 scheduled_at TIMESTAMPTZ NOT NULL,
 work_order_id BIGINT,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(company_id,schedule_id,execution_kind,scheduled_at)
);
