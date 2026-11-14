CREATE TABLE event_archive_summary (
 company_id BIGINT NOT NULL, event_id CHAR(36) NOT NULL, handler VARCHAR(80) NOT NULL,
 event_type VARCHAR(80) NOT NULL, aggregate_id BIGINT NOT NULL,
 occurred_at TIMESTAMP(6) NOT NULL, completed_at TIMESTAMP(6) NOT NULL,
 archived_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 envelope_sha256 VARCHAR(64) NOT NULL,
 attempt_count INTEGER NOT NULL, publication_count INTEGER NOT NULL, delivery_count INTEGER NOT NULL,
 retired_payload_bytes BIGINT NOT NULL,
 PRIMARY KEY(company_id,event_id,handler),
 FOREIGN KEY(company_id) REFERENCES company(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
CREATE INDEX event_archive_company_time_idx ON event_archive_summary(company_id,archived_at);
CREATE INDEX event_receipt_retention_age_idx ON consumer_receipt(completed_at,company_id,event_id,handler);
CREATE INDEX event_outbox_retention_event_idx ON domain_event_outbox(company_id,event_id);
CREATE TABLE event_retention_batch (
 id CHAR(36) PRIMARY KEY, company_id BIGINT, actor_id BIGINT,
 dry_run BOOLEAN NOT NULL, reason VARCHAR(500) NOT NULL,
 status VARCHAR(24) NOT NULL, cutoff_at TIMESTAMP(6) NOT NULL,
 started_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 finished_at TIMESTAMP(6) NULL, candidates INTEGER NOT NULL DEFAULT 0,
 archived INTEGER NOT NULL DEFAULT 0, skipped INTEGER NOT NULL DEFAULT 0, failed INTEGER NOT NULL DEFAULT 0,
 retired_payload_bytes BIGINT NOT NULL DEFAULT 0, error_code VARCHAR(120),
 FOREIGN KEY(company_id) REFERENCES company(id) ON DELETE CASCADE,
 CHECK(status IN ('RUNNING','COMPLETED','PARTIAL','FAILED','ABANDONED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
CREATE INDEX event_retention_batch_company_idx ON event_retention_batch(company_id,started_at);
CREATE INDEX event_retention_batch_status_idx ON event_retention_batch(status,started_at);
