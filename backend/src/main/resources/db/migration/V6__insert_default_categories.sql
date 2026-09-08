CREATE FUNCTION seed_default_categories(target_ledger_id uuid)
RETURNS integer
LANGUAGE sql
AS $$
	WITH inserted AS (
		INSERT INTO categories (id, ledger_id, name, sort_order)
		VALUES
			(gen_random_uuid(), target_ledger_id, '장보기', 1),
			(gen_random_uuid(), target_ledger_id, '외식', 2),
			(gen_random_uuid(), target_ledger_id, '배달', 3),
			(gen_random_uuid(), target_ledger_id, '카페/간식', 4),
			(gen_random_uuid(), target_ledger_id, '기타', 5)
		ON CONFLICT DO NOTHING
		RETURNING 1
	)
	SELECT count(*)::integer FROM inserted;
$$;
