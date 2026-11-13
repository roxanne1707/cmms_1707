-- Synthetic standalone learning data. Never used by the CMMS datasource.
CREATE TABLE spare_part (
 id BIGINT PRIMARY KEY, company_id BIGINT NOT NULL, quantity DECIMAL(12,3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0, CHECK (quantity >= 0)
) ENGINE=InnoDB;
CREATE TABLE maintenance_order (
 id BIGINT PRIMARY KEY, company_id BIGINT NOT NULL, asset_id BIGINT,
 status VARCHAR(24) NOT NULL, created_at DATETIME(6) NOT NULL, completed_on DATETIME(6),
 INDEX ix_company_asset_created (company_id,asset_id,created_at,id)
) ENGINE=InnoDB;
INSERT INTO spare_part VALUES(1,1,10,0);
INSERT INTO maintenance_order VALUES(1,1,101,'COMPLETE','2026-01-01 08:00:00','2026-01-01 09:00:00'),
 (2,1,101,'OPEN','2026-01-02 08:00:00',NULL),(3,2,201,'OPEN','2026-01-01 08:00:00',NULL);
