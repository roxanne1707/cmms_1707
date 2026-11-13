-- Use TWO independent SQL sessions. Record actual output; these are exercises, not benchmark results.
-- Session A: start at RC, lock the row and hold the transaction.
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;
START TRANSACTION;
SELECT quantity,version FROM spare_part WHERE id=1 FOR UPDATE;
UPDATE spare_part SET quantity=quantity-7,version=version+1 WHERE id=1 AND quantity>=7;
-- Session B: run the same guarded UPDATE before A commits. Observe the lock wait.
-- COMMIT A; B must see affected_rows=0 (not a negative inventory). ROLLBACK B.
-- Repeat from an explicit reset in THIS LAB ONLY, using an optimistic version predicate instead.

EXPLAIN ANALYZE SELECT id,status,created_at FROM maintenance_order
 WHERE company_id=1 AND asset_id=101 AND created_at>='2026-01-01'
 ORDER BY created_at,id LIMIT 20;
-- RC/RR: in A begin + SELECT, B inserts a new matching order and commits, then A repeats SELECT.
-- Compare plain consistent read with SELECT ... FOR UPDATE. Do not equate PG RR with InnoDB RR.
-- This file intentionally does not auto-commit the illustrative held transaction.
