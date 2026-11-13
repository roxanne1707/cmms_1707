CREATE TABLE event_retry (
 id UUID PRIMARY KEY, company_id BIGINT NOT NULL REFERENCES company(id) ON DELETE CASCADE,
 event_id UUID NOT NULL, handler VARCHAR(80) NOT NULL, replay_generation INTEGER NOT NULL DEFAULT 0,
 business_attempt INTEGER NOT NULL DEFAULT 0, state VARCHAR(30) NOT NULL DEFAULT 'READY',
 envelope TEXT NOT NULL, claim_token UUID, lease_until TIMESTAMPTZ,
 next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 last_error VARCHAR(200), created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(company_id,event_id,handler,replay_generation,business_attempt),
 CHECK (replay_generation >= 0 AND business_attempt >= 0),
 CHECK (state IN ('READY','PROCESSING','RETRY_TAKEN_OVER','DEAD','DONE'))
);
CREATE INDEX event_retry_recover_idx ON event_retry(state,lease_until);
CREATE TABLE domain_event_outbox (
 publication_id UUID PRIMARY KEY, publish_key VARCHAR(240) NOT NULL UNIQUE,
 retry_id UUID NOT NULL REFERENCES event_retry(id) ON DELETE CASCADE,
 company_id BIGINT NOT NULL REFERENCES company(id) ON DELETE CASCADE,
 event_id UUID NOT NULL, purpose VARCHAR(20) NOT NULL, dispatch TEXT NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING', publish_attempt INTEGER NOT NULL DEFAULT 0,
 claim_token UUID, lease_until TIMESTAMPTZ, next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 last_error VARCHAR(200), created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 published_at TIMESTAMPTZ,
 CHECK(status IN ('PENDING','PROCESSING','PUBLISHED')),
 CHECK(purpose IN ('EVENT','DEAD'))
);
CREATE INDEX domain_event_outbox_pending_idx ON domain_event_outbox(status,next_attempt_at,created_at);
CREATE TABLE consumer_receipt (
 company_id BIGINT NOT NULL REFERENCES company(id) ON DELETE CASCADE,
 event_id UUID NOT NULL, handler VARCHAR(80) NOT NULL,
 completed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(company_id,event_id,handler)
);
ALTER TABLE notification ADD COLUMN event_id UUID;
ALTER TABLE notification ADD COLUMN event_company_id BIGINT REFERENCES company(id) ON DELETE CASCADE;
ALTER TABLE notification ADD COLUMN event_purpose VARCHAR(80);
CREATE UNIQUE INDEX notification_event_recipient_idx
 ON notification(event_company_id,event_id,user_id,event_purpose) WHERE event_id IS NOT NULL;
CREATE TABLE event_delivery (
 id UUID PRIMARY KEY, company_id BIGINT NOT NULL REFERENCES company(id) ON DELETE CASCADE,
 event_id UUID NOT NULL, channel VARCHAR(24) NOT NULL, destination_id VARCHAR(160) NOT NULL,
 purpose VARCHAR(80) NOT NULL, payload TEXT NOT NULL,
 state VARCHAR(20) NOT NULL DEFAULT 'READY', attempt INTEGER NOT NULL DEFAULT 0,
 replay_generation INTEGER NOT NULL DEFAULT 0, claim_token UUID, lease_until TIMESTAMPTZ,
 next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 last_error VARCHAR(200), created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 sent_at TIMESTAMPTZ,
 UNIQUE(company_id,event_id,channel,destination_id,purpose),
 CHECK(state IN ('READY','PROCESSING','RETRY','SENT','DEAD','SKIPPED'))
);
CREATE INDEX event_delivery_ready_idx ON event_delivery(state,next_attempt_at,lease_until);
CREATE TABLE event_replay_audit (
 id UUID PRIMARY KEY, company_id BIGINT NOT NULL REFERENCES company(id) ON DELETE CASCADE,
 actor_id BIGINT REFERENCES own_user(id) ON DELETE SET NULL,
 resource_id UUID NOT NULL, resource_type VARCHAR(20) NOT NULL,
 reason VARCHAR(500) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE event_quarantine (
 fingerprint VARCHAR(64) PRIMARY KEY, reason VARCHAR(200) NOT NULL,
 first_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 last_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, occurrences BIGINT NOT NULL DEFAULT 1
);
