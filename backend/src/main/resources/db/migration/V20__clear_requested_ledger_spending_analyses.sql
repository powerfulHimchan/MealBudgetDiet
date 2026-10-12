-- One-time reset of the existing completed analyses in the requested ledger.
-- Keep other ledgers, in-flight requests, and analyses generated after the request.
DELETE FROM spending_analyses
WHERE ledger_id = '44df45be-f354-4896-bf7f-6f1ce74536ef'::uuid
  AND status = 'COMPLETED'
  AND analyzed_at <= TIMESTAMPTZ '2026-10-12 09:21:45+00';
