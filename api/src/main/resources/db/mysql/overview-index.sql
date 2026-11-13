-- Covers the fixed equipment work-order aggregate; keep the narrower ledger paging index.
CREATE INDEX ix_work_order_overview_cover ON work_order(company_id,asset_id,created_at,archived,is_demo,status,completed_on);
