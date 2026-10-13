package com.mealbudgetdiet.analytics.infrastructure;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.mealbudgetdiet.ai.application.AiTextResult;
import com.mealbudgetdiet.analytics.application.MidCycleAnalysisPeriod;
import com.mealbudgetdiet.analytics.application.MidCycleAnalysisSnapshot;
import com.mealbudgetdiet.budget.domain.BudgetCycle;

@Repository
public class MidCycleAnalysisRepository {
	private final JdbcTemplate jdbc;

	public MidCycleAnalysisRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public MidCycleAnalysisSnapshot snapshot(UUID ledgerId, BudgetCycle cycle, LocalDate today, Instant now) {
		boolean eligible = MidCycleAnalysisPeriod.eligible(cycle, today);
		LocalDate midpoint = MidCycleAnalysisPeriod.midpoint(cycle);
		if (!eligible) return new MidCycleAnalysisSnapshot(false, "UNAVAILABLE", null, midpoint, null, null);
		var rows = jdbc.query("""
			select status, summary, analyzed_through, analyzed_at, lease_expires_at from mid_cycle_analyses
			where ledger_id = ? and period_from = ? and period_to = ?
			""", (rs, index) -> {
			if ("COMPLETED".equals(rs.getString("status"))) {
				return new MidCycleAnalysisSnapshot(true, "COMPLETED", rs.getString("summary"), midpoint,
					rs.getObject("analyzed_through", LocalDate.class), rs.getObject("analyzed_at", OffsetDateTime.class).toInstant());
			}
			var lease = rs.getObject("lease_expires_at", OffsetDateTime.class);
			boolean processing = "PROCESSING".equals(rs.getString("status")) && lease != null && lease.toInstant().isAfter(now);
			return new MidCycleAnalysisSnapshot(true, processing ? "PROCESSING" : "NOT_ANALYZED", null, midpoint, null, null);
		}, ledgerId, cycle.from(), cycle.to());
		return rows.isEmpty() ? new MidCycleAnalysisSnapshot(true, "NOT_ANALYZED", null, midpoint, null, null) : rows.getFirst();
	}

	public boolean claim(UUID ledgerId, BudgetCycle cycle, LocalDate through, UUID token, Instant now, Instant leaseUntil) {
		return jdbc.update("""
			insert into mid_cycle_analyses (ledger_id, period_from, period_to, analyzed_through, status, claim_token, lease_expires_at, created_at, updated_at)
			values (?, ?, ?, ?, 'PROCESSING', ?, ?, ?, ?)
			on conflict (ledger_id, period_from, period_to) do update
			set status = 'PROCESSING', analyzed_through = excluded.analyzed_through, claim_token = excluded.claim_token,
			    lease_expires_at = excluded.lease_expires_at, updated_at = excluded.updated_at
			where mid_cycle_analyses.status = 'FAILED'
			   or (mid_cycle_analyses.status = 'PROCESSING' and mid_cycle_analyses.lease_expires_at <= excluded.updated_at)
			""", ledgerId, cycle.from(), cycle.to(), through, token, utc(leaseUntil), utc(now), utc(now)) == 1;
	}

	public boolean complete(UUID ledgerId, BudgetCycle cycle, UUID token, String summary, AiTextResult result, Instant now) {
		return jdbc.update("""
			update mid_cycle_analyses set status = 'COMPLETED', summary = ?, model = ?, input_tokens = ?, output_tokens = ?,
			analyzed_at = ?, updated_at = ?, claim_token = null, lease_expires_at = null
			where ledger_id = ? and period_from = ? and period_to = ? and claim_token = ? and status = 'PROCESSING'
			""", summary, result.model(), result.inputTokens(), result.outputTokens(), utc(now), utc(now), ledgerId, cycle.from(), cycle.to(), token) == 1;
	}

	public void fail(UUID ledgerId, BudgetCycle cycle, UUID token, Instant now) {
		jdbc.update("""
			update mid_cycle_analyses set status = 'FAILED', claim_token = null, lease_expires_at = null, updated_at = ?
			where ledger_id = ? and period_from = ? and period_to = ? and claim_token = ? and status = 'PROCESSING'
			""", utc(now), ledgerId, cycle.from(), cycle.to(), token);
	}

	private static OffsetDateTime utc(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}
}
