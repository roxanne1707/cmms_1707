package com.grash.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grash.exception.CustomException;
import com.grash.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;

/** A command result is committed atomically with its domain mutation. No TTL silently forgets deduplication. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class DomainCommandStore {
    private final JdbcTemplate jdbc;
    private final com.grash.database.DatabasePlatform database;
    private final ObjectMapper json;
    public record Stored(String payloadHash, Long actorId, String subjectType, Long subjectId, String responseJson) {}

    public Optional<Stored> begin(String namespace, String key, User user, String payload) {
        if (key == null) return Optional.empty(); // legacy callers have no network-replay guarantee
        if (!key.matches("[A-Za-z0-9._:-]{1,128}"))
            throw new CustomException("Invalid Idempotency-Key", HttpStatus.BAD_REQUEST);
        long companyId = user.getCompany().getId();
        byte[] digest = digest(namespace + ":" + companyId + ":" + key);
        // Database transaction scoped; even a hash collision merely serializes unrelated commands.
        database.lock(namespace + ":" + companyId + ":" + key);
        List<Stored> rows = jdbc.query("SELECT payload_hash,actor_id,subject_type,subject_id,response_json FROM domain_command WHERE company_id=? AND namespace=? AND command_key=?",
                (rs, n) -> new Stored(rs.getString(1), rs.getLong(2), rs.getString(3), rs.getLong(4), rs.getString(5)), companyId, namespace, key);
        if (rows.isEmpty()) return Optional.empty();
        Stored stored = rows.get(0);
        if (!stored.payloadHash().equals(hash(payload)) || !stored.actorId().equals(user.getId()))
            throw new CustomException("Idempotency-Key was already used for a different command", HttpStatus.CONFLICT);
        return Optional.of(stored);
    }

    public void complete(String namespace, String key, User user, String payload, String subjectType, Long subjectId, Object result) {
        if (key == null) return;
        try {
            jdbc.update("INSERT INTO domain_command(company_id,namespace,command_key,payload_hash,actor_id,subject_type,subject_id,response_json) VALUES (?,?,?,?,?,?,?,?)",
                    user.getCompany().getId(), namespace, key, hash(payload), user.getId(), subjectType, subjectId, json.writeValueAsString(result));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("Cannot record command result", ex); }
    }
    public <T> T result(Stored stored, TypeReference<T> type) {
        try { return json.readValue(stored.responseJson(), type); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("Invalid stored command result", ex); }
    }
    public static String hash(String value) { return java.util.HexFormat.of().formatHex(digest(value)); }
    private static byte[] digest(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
