package com.grash.security;

import com.grash.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/** Durable login families. No cached revocation decisions: the primary database is authoritative. */
@Service
@RequiredArgsConstructor
public class SessionRegistry {
    private final JdbcTemplate jdbc;
    private final com.grash.database.DatabasePlatform database;

    public record SessionView(UUID id, Instant createdAt, Instant lastRotatedAt, Instant expiresAt,
                              Instant revokedAt, String userAgent, boolean current) {}

    // All login/rotation/revocation paths acquire the user row before token/session rows.
    // This also serializes logout-all against a concurrent login or refresh.
    @Transactional
    public void lockUser(Long userId) {
        Boolean enabled = jdbc.query("SELECT enabled FROM own_user WHERE id=? FOR UPDATE",
                (rs,n)->rs.getBoolean(1),userId).stream().findFirst().orElse(false);
        if (!enabled) rejected("Account is disabled or missing");
    }

    @Transactional
    public void lockRefreshOwner(String hash) {
        jdbc.query(database.sql("SELECT u.id FROM own_user u JOIN refresh_token t ON t.user_id=u.id WHERE t.token_hash=? FOR UPDATE OF u",
                "SELECT u.id FROM own_user u WHERE u.id=(SELECT t.user_id FROM refresh_token t WHERE t.token_hash=?) FOR UPDATE"),
                (rs,n)->rs.getLong(1),hash);
    }

    @Transactional
    public UUID create(Long userId, Date expiresAt) {
        lockUser(userId);
        UUID id=UUID.randomUUID();
        String agent="Unknown client";
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            String supplied=attrs.getRequest().getHeader("User-Agent");
            if (supplied!=null) agent=supplied.replaceAll("[\\r\\n\\p{Cntrl}]", " ");
        }
        if (agent.length()>255) agent=agent.substring(0,255);
        jdbc.update("INSERT INTO auth_session(id,user_id,created_at,last_rotated_at,expires_at,user_agent) " +
                database.sql("VALUES(?,?,clock_timestamp(),clock_timestamp(),?,?)","VALUES(?,?,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6),?,?)"),id,userId,new Timestamp(expiresAt.getTime()),agent);
        return id;
    }

    @Transactional(readOnly=true)
    public void assertActive(UUID id,Long userId) {
        Boolean active=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM auth_session s JOIN own_user u ON u.id=s.user_id " +
                database.sql("WHERE s.id=? AND s.user_id=? AND s.revoked_at IS NULL AND s.expires_at>clock_timestamp() AND u.enabled)","WHERE s.id=? AND s.user_id=? AND s.revoked_at IS NULL AND s.expires_at>CURRENT_TIMESTAMP(6) AND u.enabled)"),
                Boolean.class,id,userId);
        if (!Boolean.TRUE.equals(active)) rejected("Session has been revoked or expired. Please sign in again");
    }

    @Transactional
    public void rotate(UUID id,Long userId,Date expiresAt) {
        lockUser(userId);
        int updated=jdbc.update(database.sql("UPDATE auth_session SET last_rotated_at=clock_timestamp(),expires_at=? ","UPDATE auth_session SET last_rotated_at=CURRENT_TIMESTAMP(6),expires_at=? ") +
                database.sql("WHERE id=? AND user_id=? AND revoked_at IS NULL AND expires_at>clock_timestamp()","WHERE id=? AND user_id=? AND revoked_at IS NULL AND expires_at>CURRENT_TIMESTAMP(6)"),
                new Timestamp(expiresAt.getTime()),id,userId);
        if(updated!=1) rejected("Session has been revoked or expired. Please sign in again");
    }

    @Transactional
    public void revoke(UUID id,Long userId) {
        lockUser(userId);
        // Ownership is part of both reads and writes; UUID knowledge never grants access.
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM auth_session WHERE id=? AND user_id=?)",
                Boolean.class,id,userId))) throw new CustomException("Session not found",HttpStatus.NOT_FOUND);
        jdbc.update(database.sql("UPDATE auth_session SET revoked_at=coalesce(revoked_at,clock_timestamp()) WHERE id=? AND user_id=?","UPDATE auth_session SET revoked_at=coalesce(revoked_at,CURRENT_TIMESTAMP(6)) WHERE id=? AND user_id=?"),id,userId);
        jdbc.update("UPDATE refresh_token SET revoked=true WHERE session_id=? AND user_id=?",id,userId);
    }

    @Transactional
    public void revokeAll(Long userId) {
        // Disabled accounts must still be revocable; unlike a login, no enabled predicate here.
        jdbc.query("SELECT id FROM own_user WHERE id=? FOR UPDATE",(rs,n)->rs.getLong(1),userId);
        jdbc.update(database.sql("UPDATE own_user SET session_invalidated_at=clock_timestamp() WHERE id=?","UPDATE own_user SET session_invalidated_at=CURRENT_TIMESTAMP(6) WHERE id=?"),userId);
        jdbc.update(database.sql("UPDATE auth_session SET revoked_at=coalesce(revoked_at,clock_timestamp()) WHERE user_id=?","UPDATE auth_session SET revoked_at=coalesce(revoked_at,CURRENT_TIMESTAMP(6)) WHERE user_id=?"),userId);
        jdbc.update("UPDATE refresh_token SET revoked=true WHERE user_id=?",userId);
    }

    @Transactional(readOnly=true)
    public List<SessionView> list(Long userId,UUID current) {
        return jdbc.query("SELECT id,created_at,last_rotated_at,expires_at,revoked_at,user_agent FROM auth_session " +
                "WHERE user_id=? ORDER BY created_at DESC,id LIMIT 100",(rs,n)-> {
            UUID id=com.grash.database.DatabasePlatform.uuid(rs,"id");Timestamp revoked=rs.getTimestamp("revoked_at");
            return new SessionView(id,rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("last_rotated_at").toInstant(),
                    rs.getTimestamp("expires_at").toInstant(),revoked==null?null:revoked.toInstant(),rs.getString("user_agent"),id.equals(current));
        },userId);
    }

    private static void rejected(String message) { throw new CustomException(message,HttpStatus.UNAUTHORIZED); }
}
