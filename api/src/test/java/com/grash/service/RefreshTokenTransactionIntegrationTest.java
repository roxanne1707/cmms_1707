package com.grash.service;

import com.grash.dto.AuthTokens;
import com.grash.exception.CustomException;
import com.grash.model.RefreshToken;
import com.grash.model.Role;
import com.grash.model.User;
import com.grash.model.enums.RoleType;
import com.grash.repository.RefreshTokenRepository;
import com.grash.security.JwtTokenProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;

import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Tests the real Spring transaction interceptor against an isolated database table.
 * Repository operations use JDBC test doubles: this does not prove Hibernate's entity fetch plan. */
@EnabledIfSystemProperty(named = "cmms.test.jdbc-url", matches = ".+")
class RefreshTokenTransactionIntegrationTest {
    private JdbcTemplate jdbc;
    private String schema;
    private boolean mysql;
    private RefreshTokenService target;
    private RefreshTokenService service;
    private DataSourceTransactionManager manager;
    private JwtTokenProvider jwt;
    private User user;

    @BeforeEach void setUp() {
        String url = System.getProperty("cmms.test.jdbc-url");
        mysql=url.equals("jdbc:mysql://127.0.0.1:23306/cmms_mysql_test");
        assertTrue(mysql || url.startsWith("jdbc:postgresql://127.0.0.1:25432/cmms_enhancement_test"),
                "These tests only accept the dedicated local test instance");
        schema = (mysql?"cmms_mysql_refresh_":"refresh_test_") + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource ds = new DriverManagerDataSource(url,
                System.getProperty("cmms.test.jdbc-user", "cmms_test"),
                System.getProperty("cmms.test.jdbc-password", "cmms_test_local"));
        jdbc = new JdbcTemplate(ds);
        jdbc.execute(mysql?"CREATE DATABASE "+schema+" CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs":"CREATE SCHEMA " + schema);
        if(mysql) jdbc.execute("CREATE TABLE "+schema+".token(id BIGINT AUTO_INCREMENT PRIMARY KEY,hash VARCHAR(64) UNIQUE,revoked BOOLEAN NOT NULL,created_at DATETIME(6),expires_at DATETIME(6),replacement VARCHAR(64))");
        else
        jdbc.execute("CREATE TABLE " + schema + ".token (id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, hash text UNIQUE," +
                " revoked boolean NOT NULL, created_at timestamp, expires_at timestamp, replacement text)");
        manager = new DataSourceTransactionManager(ds);
        user = new User(); user.setId(1L); user.setEmail("test@example.invalid"); user.setEnabled(true);
        user.setRole(Role.builder().id(1L).roleType(RoleType.ROLE_CLIENT).build());
        RefreshTokenRepository repo = mock(RefreshTokenRepository.class);
        jwt = mock(JwtTokenProvider.class);
        when(jwt.createToken(anyString(), anyList(), any(UUID.class))).thenReturn("local-test-access");
        when(jwt.computeAccessTokenExpiration()).thenReturn(new Date(System.currentTimeMillis()+60000));
        when(repo.findByTokenHash(anyString())).thenAnswer(inv -> jdbc.query(
                "SELECT * FROM " + schema + ".token WHERE hash=? FOR UPDATE", (rs, row) -> RefreshToken.builder()
                        .id(rs.getLong("id")).tokenHash(rs.getString("hash")).user(user)
                        .revoked(rs.getBoolean("revoked")).createdAt(rs.getTimestamp("created_at"))
                        .expiresAt(rs.getTimestamp("expires_at")).replacedByTokenHash(rs.getString("replacement")).build(),
                (String) inv.getArgument(0)).stream().findFirst());
        when(repo.save(any(RefreshToken.class))).thenAnswer(inv -> {
            RefreshToken t = inv.getArgument(0);
            if (t.getId() == null) {
                String insert="INSERT INTO "+schema+".token(hash,revoked,created_at,expires_at,replacement) VALUES(?,?,?,?,?)";
                Object[] values={t.getTokenHash(),t.isRevoked(),new Timestamp(t.getCreatedAt().getTime()),new Timestamp(t.getExpiresAt().getTime()),t.getReplacedByTokenHash()};
                if(mysql){jdbc.update(insert,values);t.setId(jdbc.queryForObject("SELECT id FROM "+schema+".token WHERE hash=?",Long.class,t.getTokenHash()));}
                else t.setId(jdbc.queryForObject(insert+" RETURNING id",Long.class,values));
            } else jdbc.update("UPDATE " + schema + ".token SET revoked=?, replacement=? WHERE id=?",
                    t.isRevoked(),t.getReplacedByTokenHash(),t.getId());
            return t;
        });
        doAnswer(inv -> { jdbc.update("UPDATE " + schema + ".token SET revoked=true"); return null; })
                .when(repo).revokeAllByUser(1L);
        com.grash.security.SessionRegistry registry=mock(com.grash.security.SessionRegistry.class);
        when(registry.create(anyLong(),any(Date.class))).thenAnswer(inv->UUID.randomUUID());
        target = new RefreshTokenService(repo,jwt,registry);
        ReflectionTestUtils.setField(target,"refreshTokenValidityInMilliseconds",604800000L);
        service = proxy(false);
    }
    private RefreshTokenService proxy(boolean legacyRollback) {
        ProxyFactory factory = new ProxyFactory(target); factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(manager, legacyRollback
                ? (method,clazz) -> new DefaultTransactionAttribute()
                : new AnnotationTransactionAttributeSource()));
        return (RefreshTokenService)factory.getProxy();
    }
    private void seed(String raw,boolean revoked,boolean expired) {
        jdbc.update("INSERT INTO " + schema + ".token(hash,revoked,created_at,expires_at) VALUES(?,?,?,?)",
                target.hash(raw),revoked,new Timestamp(System.currentTimeMillis()-2000),
                new Timestamp(System.currentTimeMillis()+(expired ? -1000 : 60000)));
    }
    private int active() { return jdbc.queryForObject("SELECT count(*) FROM " + schema + ".token WHERE NOT revoked",Integer.class); }
    @AfterEach void cleanup() {
        if (!mysql && jdbc != null && schema != null && schema.matches("refresh_test_[a-f0-9]{32}"))
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
    }
    @Test void legacyRollbackRuleReproducesRevocationLoss() {
        seed("old",true,false);seed("active",false,false);
        assertThrows(CustomException.class,()->proxy(true).rotate("old"));
        assertEquals(1,active(),"Default RuntimeException rollback loses the revoke-all update");
    }
    @Test void reuseRejectionCommitsRevocation() {
        seed("old",true,false);seed("active",false,false);
        assertThrows(CustomException.class,()->service.rotate("old"));
        assertEquals(0,active());
    }
    @Test void expiryRejectionCommitsRevocation() {
        seed("expired",false,true);
        assertThrows(CustomException.class,()->service.rotate("expired"));
        assertEquals(0,active());
    }
    @Test void failedSigningRollsBackNewTokenAndSourceRevocation() {
        seed("valid",false,false);
        when(jwt.createToken(anyString(),anyList(),any(UUID.class))).thenThrow(new IllegalStateException("injected signing failure"));
        assertThrows(IllegalStateException.class,()->service.rotate("valid"));
        assertEquals(1,active());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM " + schema + ".token",Integer.class));
    }
    @Test void disabledAccountCannotRotate() {
        seed("valid",false,false);user.setEnabled(false);
        assertThrows(CustomException.class,()->service.rotate("valid"));
        assertEquals(0,active());
    }
    @Test void concurrentSourceRotationHasOneSuccessAndReuseRevokesRefreshFamily() throws Exception {
        seed("valid",false,false);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        CyclicBarrier barrier=new CyclicBarrier(2);
        try {
            Callable<Boolean> action=()->{barrier.await(5,TimeUnit.SECONDS);try {service.rotate("valid");return true;}catch(CustomException e){return false;}};
            Future<Boolean> first=pool.submit(action), second=pool.submit(action);
            assertNotEquals(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS));
            assertEquals(0,active(),"The established reuse policy revokes refresh sessions after the second request");
        } finally {pool.shutdownNow();}
    }
}
