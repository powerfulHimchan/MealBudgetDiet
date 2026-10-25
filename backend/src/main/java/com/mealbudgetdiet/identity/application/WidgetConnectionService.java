package com.mealbudgetdiet.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.mealbudgetdiet.ledger.application.LedgerAccessService;
import com.mealbudgetdiet.shared.api.ApiException;

/** A read-only capability bound to the approving web session and its original ledger. */
@Service
public class WidgetConnectionService {
    private final JdbcTemplate jdbc;
    private final LedgerAccessService access;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public WidgetConnectionService(JdbcTemplate jdbc, LedgerAccessService access, Clock clock) {
        this.jdbc = jdbc;
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    public void authorize(UUID userId, String sessionId, String challenge) {
        UUID ledgerId = access.requireActiveMembership(userId).getId().getLedgerId();
        jdbc.update("delete from widget_connections where expires_at <= ? or (token_hash is null and claim_until <= ?)",
            Timestamp.from(clock.instant()), Timestamp.from(clock.instant()));
        // Limit grants per session; the challenge is generated on-device, never a browser secret.
        Integer count = jdbc.queryForObject("select count(*) from widget_connections where session_id = ?", Integer.class, sessionId);
        if (count != null && count >= 20) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
            "WIDGET_CONNECTION_LIMIT", "연결된 위젯을 해제한 후 다시 시도해 주세요.");
        int inserted = jdbc.update("""
            insert into widget_connections(id, session_id, user_id, ledger_id, challenge, claim_until, expires_at)
            values (?, ?, ?, ?, ?, ?, ?) on conflict (challenge) do nothing
            """, UUID.randomUUID(), sessionId, userId, ledgerId, challenge,
            Timestamp.from(clock.instant().plus(Duration.ofMinutes(5))),
            Timestamp.from(clock.instant().plus(Duration.ofDays(30))));
        if (inserted != 1) throw new ApiException(HttpStatus.CONFLICT, "WIDGET_ALREADY_AUTHORIZED", "앱에서 위젯 연결을 다시 시작해 주세요.");
    }

    @Transactional
    public String claim(String verifier) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        int updated = jdbc.update("""
            update widget_connections w set challenge = null, token_hash = ?
            where challenge = ? and token_hash is null and claim_until > ? and expires_at > ?
              and exists (select 1 from spring_session s where s.session_id = w.session_id and s.expiry_time > ?)
            """, hash(token), hash(verifier), Timestamp.from(clock.instant()), Timestamp.from(clock.instant()), clock.millis());
        if (updated != 1) throw unauthorized();
        return token;
    }

    @Transactional(readOnly = true)
    public UUID resolve(String authorization) {
        if (authorization == null || !authorization.matches("Bearer [A-Za-z0-9_-]{43}")) throw unauthorized();
        var users = jdbc.query("""
            select w.user_id from widget_connections w
            join spring_session s on s.session_id = w.session_id
            join users u on u.id = w.user_id and u.status = 'ACTIVE' and u.email = s.principal_name
            join ledger_members m on m.user_id = w.user_id and m.ledger_id = w.ledger_id and m.status = 'ACTIVE'
            join ledgers l on l.id = w.ledger_id and l.status = 'ACTIVE'
            where w.token_hash = ? and w.expires_at > ? and s.expiry_time > ?
            """, (rs, row) -> rs.getObject(1, UUID.class), hash(authorization.substring(7)),
            Timestamp.from(clock.instant()), clock.millis());
        if (users.size() != 1) throw unauthorized();
        return users.getFirst();
    }

    public void revokeSession(String sessionId) {
        jdbc.update("delete from widget_connections where session_id = ?", sessionId);
    }

    public static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "WIDGET_AUTHENTICATION_REQUIRED", "앱에서 위젯을 다시 연결해 주세요.");
    }
}
