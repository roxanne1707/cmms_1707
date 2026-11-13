package com.grash.database;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/** Explicit SQL choices. The domain transaction and its invariants remain unchanged. */
@Component
public class DatabasePlatform {
    private final boolean mysql;
    private final JdbcTemplate jdbc;
    public DatabasePlatform(@Value("${cmms.database:postgresql}") String database, JdbcTemplate jdbc) {
        if (!database.equals("mysql") && !database.equals("postgresql")) throw new IllegalArgumentException("Unsupported database");
        this.mysql=database.equals("mysql"); this.jdbc=jdbc;
    }
    public boolean isMysql() { return mysql; }
    public String sql(String postgresql, String mysqlSql) { return mysql ? mysqlSql : postgresql; }
    public static UUID uuid(ResultSet rs, int column) throws SQLException {
        String value=rs.getString(column); return value==null?null:UUID.fromString(value);
    }
    public static UUID uuid(ResultSet rs, String column) throws SQLException {
        String value=rs.getString(column); return value==null?null:UUID.fromString(value);
    }
    public void lock(String key) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Transaction required for database mutex");
        String digest=com.grash.service.DomainCommandStore.hash(key);
        if (!mysql) {
            jdbc.queryForList("SELECT pg_advisory_xact_lock(?)",Long.parseUnsignedLong(digest.substring(0,16),16));
        } else {
            // InnoDB row lock is released by commit/rollback. GET_LOCK is deliberately not used.
            jdbc.update("INSERT INTO cmms_transaction_lock(lock_key) VALUES (?) ON DUPLICATE KEY UPDATE lock_key=lock_key",digest);
            jdbc.queryForList("SELECT lock_key FROM cmms_transaction_lock WHERE lock_key=? FOR UPDATE",digest);
        }
    }
    public long nextNotificationId() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Transaction required for ID allocation");
        if (!mysql) return jdbc.queryForObject("SELECT nextval('notification_seq')",Long.class);
        Long high=jdbc.queryForObject("SELECT next_val FROM notification_seq FOR UPDATE",Long.class);
        // Reserve a complete Hibernate allocation block; never overlap ORM-allocated IDs.
        jdbc.update("UPDATE notification_seq SET next_val=next_val+50");
        return high;
    }
}
