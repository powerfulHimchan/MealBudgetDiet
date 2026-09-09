package com.mealbudgetdiet.notification.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.mealbudgetdiet.notification.application.PushDeliveryTask;
import com.mealbudgetdiet.notification.domain.BudgetAlertType;

@Repository
public class PushOutboxStore {

	private static final int MAX_ATTEMPTS = 4;

	private final JdbcTemplate jdbcTemplate;

	public PushOutboxStore(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional
	public List<PushDeliveryTask> claim(int batchSize) {
		return jdbcTemplate.query("""
			with candidates as (
			  select id
			  from push_deliveries
			  where exists (
			    select 1 from push_subscriptions subscription
			    where subscription.id = push_deliveries.push_subscription_id and subscription.status = 'ACTIVE'
			  ) and attempt_count < ? and (
			    (status in ('PENDING', 'FAILED') and next_attempt_at is not null and next_attempt_at <= current_timestamp)
			    or (status = 'SENDING' and updated_at < current_timestamp - interval '5 minutes')
			  )
			  order by created_at
			  for update skip locked
			  limit ?
			), claimed as (
			  update push_deliveries delivery
			  set status = 'SENDING', attempt_count = delivery.attempt_count + 1,
			      next_attempt_at = null, updated_at = current_timestamp
			  from candidates
			  where delivery.id = candidates.id
			  returning delivery.id, delivery.budget_alert_id, delivery.push_subscription_id,
			            delivery.attempt_count
			)
			select claimed.id delivery_id, claimed.attempt_count, alert.id alert_id, alert.alert_type,
			       alert.alert_month, alert.monthly_budget, alert.total_spent, alert.remaining_days,
			       subscription.id subscription_id, subscription.endpoint,
			       subscription.p256dh_key, subscription.auth_key
			from claimed
			join budget_alerts alert on alert.id = claimed.budget_alert_id
			join push_subscriptions subscription on subscription.id = claimed.push_subscription_id
			where subscription.status = 'ACTIVE'
			""", (resultSet, rowNumber) -> new PushDeliveryTask(
				resultSet.getObject("delivery_id", UUID.class),
				resultSet.getInt("attempt_count"),
				resultSet.getObject("alert_id", UUID.class),
				BudgetAlertType.valueOf(resultSet.getString("alert_type")),
				resultSet.getObject("alert_month", java.time.LocalDate.class),
				resultSet.getLong("monthly_budget"),
				resultSet.getLong("total_spent"),
				resultSet.getInt("remaining_days"),
				resultSet.getObject("subscription_id", UUID.class),
				resultSet.getString("endpoint"),
				resultSet.getString("p256dh_key"),
				resultSet.getString("auth_key")
			), MAX_ATTEMPTS, batchSize);
	}

	public void markSent(UUID deliveryId) {
		jdbcTemplate.update("""
			update push_deliveries
			set status = 'SENT', sent_at = current_timestamp, last_error = null,
			    next_attempt_at = null, updated_at = current_timestamp
			where id = ?
			""", deliveryId);
	}

	public void markFailed(PushDeliveryTask task, boolean expired, boolean retryable, String rawError) {
		String error = rawError == null ? "알 수 없는 Web Push 오류" : rawError.substring(0, Math.min(1000, rawError.length()));
		if (expired) {
			jdbcTemplate.update(
				"update push_subscriptions set status = 'EXPIRED', updated_at = current_timestamp where id = ?",
				task.subscriptionId());
		}
		Integer retryMinutes = retryable && !expired && task.attemptCount() < MAX_ATTEMPTS
			? 1 << (task.attemptCount() - 1)
			: null;
		jdbcTemplate.update("""
			update push_deliveries
			set status = 'FAILED', last_error = ?,
			    next_attempt_at = case when ?::integer is null then null else current_timestamp + (? * interval '1 minute') end,
			    updated_at = current_timestamp
			where id = ?
			""", error, retryMinutes, retryMinutes, task.deliveryId());
	}
}
