-- One-time reset requested on 2026-10-13 so the ledger can be analyzed with merchant and memo context.
-- Preserve other ledgers, in-flight requests, and results generated after the request.
DELETE FROM spending_analyses
WHERE ledger_id = '44df45be-f354-4896-bf7f-6f1ce74536ef'::uuid
  AND status = 'COMPLETED'
  AND analyzed_at <= TIMESTAMPTZ '2026-10-13 06:18:59+00';
