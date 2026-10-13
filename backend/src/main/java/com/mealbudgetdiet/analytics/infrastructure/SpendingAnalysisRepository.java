package com.mealbudgetdiet.analytics.infrastructure;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.mealbudgetdiet.analytics.application.SpendingAnalysisSnapshot;
import com.mealbudgetdiet.ai.application.AiTextResult;

@Repository
public class SpendingAnalysisRepository {
	private final JdbcTemplate jdbc;

	public SpendingAnalysisRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public ExpenseDetails expenseDetails(UUID ledgerId, LocalDate from, LocalDate to) {
		var rows = jdbc.query("""
			select e.spent_on, e.amount, coalesce(c.name, '분류 없음') category_name,
			       e.merchant, e.memo, count(*) over () total_count
			from expenses e
			left join categories c on c.id = e.category_id
			where e.ledger_id = ? and e.spent_on between ? and ?
			order by e.amount desc, e.spent_on desc, e.id desc
			limit 200
			""", (rs, index) -> new ExpenseDetailRow(rs.getLong("total_count"), new ExpenseDetail(
				rs.getObject("spent_on", LocalDate.class), rs.getLong("amount"), rs.getString("category_name"),
				rs.getString("merchant"), rs.getString("memo"))), ledgerId, from, to);
		return new ExpenseDetails(rows.isEmpty() ? 0 : rows.getFirst().totalCount(), rows.stream().map(ExpenseDetailRow::detail).toList());
	}

	public record ExpenseDetail(LocalDate spentOn, long amount, String categoryName, String merchant, String memo) {
	}

	public record ExpenseDetails(long totalCount, List<ExpenseDetail> items) {
	}

	private record ExpenseDetailRow(long totalCount, ExpenseDetail detail) {
	}

	public SpendingAnalysisSnapshot snapshot(UUID ledgerId, LocalDate from, LocalDate to, boolean eligible, Instant now) {
		var rows = jdbc.query("""
			select status, summary, analyzed_at, lease_expires_at from spending_analyses
			where ledger_id = ? and period_from = ? and period_to = ?
			""", (rs, index) -> {
			String status = rs.getString("status");
			if ("COMPLETED".equals(status)) {
				return new SpendingAnalysisSnapshot(eligible, status, rs.getString("summary"),
					rs.getObject("analyzed_at", OffsetDateTime.class).toInstant());
			}
			var lease = rs.getObject("lease_expires_at", OffsetDateTime.class);
			boolean processing = "PROCESSING".equals(status) && lease != null && lease.toInstant().isAfter(now);
			return new SpendingAnalysisSnapshot(eligible, processing ? "PROCESSING" : "NOT_ANALYZED", null, null);
		}, ledgerId, from, to);
		return rows.isEmpty() ? new SpendingAnalysisSnapshot(eligible, eligible ? "NOT_ANALYZED" : "UNAVAILABLE", null, null) : rows.getFirst();
	}

	public boolean claim(UUID ledgerId, LocalDate from, LocalDate to, UUID token, Instant now, Instant leaseUntil) {
		return jdbc.update("""
			insert into spending_analyses (ledger_id, period_from, period_to, status, claim_token, lease_expires_at, created_at, updated_at)
			values (?, ?, ?, 'PROCESSING', ?, ?, ?, ?)
			on conflict (ledger_id, period_from, period_to) do update
			set status = 'PROCESSING', claim_token = excluded.claim_token,
			    lease_expires_at = excluded.lease_expires_at, updated_at = excluded.updated_at
			where spending_analyses.status = 'FAILED'
			   or (spending_analyses.status = 'PROCESSING' and spending_analyses.lease_expires_at <= excluded.updated_at)
			""", ledgerId, from, to, token, utc(leaseUntil), utc(now), utc(now)) == 1;
	}

	public boolean complete(UUID ledgerId, LocalDate from, LocalDate to, UUID token, String summary, AiTextResult result, Instant now) {
		return jdbc.update("""
			update spending_analyses set status = 'COMPLETED', summary = ?, model = ?, input_tokens = ?, output_tokens = ?,
			analyzed_at = ?, updated_at = ?, claim_token = null, lease_expires_at = null
			where ledger_id = ? and period_from = ? and period_to = ? and claim_token = ? and status = 'PROCESSING'
			""", summary, result.model(), result.inputTokens(), result.outputTokens(), utc(now), utc(now), ledgerId, from, to, token) == 1;
	}

	public void fail(UUID ledgerId, LocalDate from, LocalDate to, UUID token, Instant now) {
		jdbc.update("""
			update spending_analyses set status = 'FAILED', claim_token = null, lease_expires_at = null, updated_at = ?
			where ledger_id = ? and period_from = ? and period_to = ? and claim_token = ? and status = 'PROCESSING'
			""", utc(now), ledgerId, from, to, token);
	}

	private static OffsetDateTime utc(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}
}
