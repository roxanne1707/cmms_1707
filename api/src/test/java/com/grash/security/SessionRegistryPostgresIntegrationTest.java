package com.grash.security;

import com.grash.dto.AuthTokens;
import com.grash.exception.CustomException;
import com.grash.model.*;
import com.grash.model.enums.RoleType;
import com.grash.repository.RefreshTokenRepository;
import com.grash.service.RefreshTokenService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real database locks and transactions; refresh repository is a JDBC-backed test double, not Hibernate. */
@EnabledIfSystemProperty(named="cmms.test.jdbc-url",matches=".+")
class SessionRegistryPostgresIntegrationTest {
    JdbcTemplate admin,jdbc;String schema;boolean mysql;DataSourceTransactionManager tx;
    SessionRegistry sessions;RefreshTokenService tokens;JwtTokenProvider jwt;User user;
    @BeforeEach void setUp() {
        String url=System.getProperty("cmms.test.jdbc-url");
        mysql=url.equals("jdbc:mysql://127.0.0.1:23306/cmms_mysql_test");
        assertTrue(mysql || url.matches("jdbc:postgresql://127\\.0\\.0\\.1:25432/cmms_enhancement_test(?:\\?.*)?"));
        String username=System.getProperty("cmms.test.jdbc-user","cmms_test"),password=System.getProperty("cmms.test.jdbc-password","cmms_test_local");
        admin=new JdbcTemplate(new DriverManagerDataSource(url,username,password));
        if(mysql) {
            schema="cmms_mysql_session_"+UUID.randomUUID().toString().replace("-","");
            admin.execute("CREATE DATABASE "+schema+" CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs");
            DriverManagerDataSource ds=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:23306/"+schema+"?useAffectedRows=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true",username,password);
            jdbc=new com.grash.database.MysqlJdbcConfiguration().jdbcTemplate(ds);tx=new DataSourceTransactionManager(ds);
            jdbc.execute("CREATE TABLE own_user(id BIGINT PRIMARY KEY,enabled BOOLEAN NOT NULL,session_invalidated_at DATETIME(6))");
            jdbc.execute("INSERT INTO own_user(id,enabled) VALUES(1,true),(2,true)");
            jdbc.execute("CREATE TABLE auth_session(id CHAR(36) PRIMARY KEY,user_id BIGINT,created_at DATETIME(6),last_rotated_at DATETIME(6),expires_at DATETIME(6),revoked_at DATETIME(6),user_agent VARCHAR(255),FOREIGN KEY(user_id) REFERENCES own_user(id))");
            jdbc.execute("CREATE TABLE refresh_token(id BIGINT AUTO_INCREMENT PRIMARY KEY,token_hash VARCHAR(64) UNIQUE,user_id BIGINT,created_at DATETIME(6),expires_at DATETIME(6),revoked BOOLEAN,replaced_by_token_hash VARCHAR(64),session_id CHAR(36),FOREIGN KEY(user_id) REFERENCES own_user(id),FOREIGN KEY(session_id) REFERENCES auth_session(id))");
        } else {
        schema="session_test_"+UUID.randomUUID().toString().replace("-","");admin.execute("CREATE SCHEMA "+schema);
        DriverManagerDataSource ds=new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,username,password);
        jdbc=new JdbcTemplate(ds);tx=new DataSourceTransactionManager(ds);
        jdbc.execute("CREATE TABLE own_user(id bigint PRIMARY KEY,enabled boolean NOT NULL,session_invalidated_at timestamptz)");
        jdbc.execute("INSERT INTO own_user(id,enabled) VALUES(1,true),(2,true)");
        jdbc.execute("CREATE TABLE auth_session(id uuid PRIMARY KEY,user_id bigint REFERENCES own_user(id),created_at timestamptz,last_rotated_at timestamptz,expires_at timestamptz,revoked_at timestamptz,user_agent varchar(255))");
        jdbc.execute("CREATE TABLE refresh_token(id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,token_hash text UNIQUE,user_id bigint REFERENCES own_user(id),created_at timestamptz,expires_at timestamptz,revoked boolean,replaced_by_token_hash text,session_id uuid REFERENCES auth_session(id))");

        }
        sessions=proxy(new SessionRegistry(jdbc,new com.grash.database.DatabasePlatform(mysql?"mysql":"postgresql",jdbc)));
        user=new User();user.setId(1L);user.setEmail("session-test@example.invalid");user.setEnabled(true);user.setRole(Role.builder().roleType(RoleType.ROLE_CLIENT).build());
        CustomUserDetailsService details=mock(CustomUserDetailsService.class);
        when(details.loadUserByUsername(user.getEmail())).thenReturn(CustomUserDetail.builder().user(user).build());
        jwt=new JwtTokenProvider(details,sessions);ReflectionTestUtils.setField(jwt,"secretKey",Base64.getEncoder().encodeToString(new byte[32]));ReflectionTestUtils.setField(jwt,"validityInMilliseconds",3600000L);jwt.init();
        RefreshTokenRepository repo=mock(RefreshTokenRepository.class);
        when(repo.findByTokenHash(anyString())).thenAnswer(inv->jdbc.query("SELECT * FROM refresh_token WHERE token_hash=? FOR UPDATE",(rs,n)->RefreshToken.builder().id(rs.getLong("id")).tokenHash(rs.getString("token_hash")).user(user).createdAt(rs.getTimestamp("created_at")).expiresAt(rs.getTimestamp("expires_at")).revoked(rs.getBoolean("revoked")).replacedByTokenHash(rs.getString("replaced_by_token_hash")).sessionId(com.grash.database.DatabasePlatform.uuid(rs,"session_id")).build(),(String)inv.getArgument(0)).stream().findFirst());
        when(repo.save(any(RefreshToken.class))).thenAnswer(inv->{
            RefreshToken t=inv.getArgument(0);
            if(t.getId()==null) {
                String insert="INSERT INTO refresh_token(token_hash,user_id,created_at,expires_at,revoked,replaced_by_token_hash,session_id) VALUES(?,?,?,?,?,?,?)";
                Object[] values={t.getTokenHash(),t.getUser().getId(),new Timestamp(t.getCreatedAt().getTime()),new Timestamp(t.getExpiresAt().getTime()),t.isRevoked(),t.getReplacedByTokenHash(),t.getSessionId()};
                if(mysql){jdbc.update(insert,values);t.setId(jdbc.queryForObject("SELECT id FROM refresh_token WHERE token_hash=?",Long.class,t.getTokenHash()));}
                else t.setId(jdbc.queryForObject(insert+" RETURNING id",Long.class,values));
            } else jdbc.update("UPDATE refresh_token SET revoked=?,replaced_by_token_hash=? WHERE id=?",t.isRevoked(),t.getReplacedByTokenHash(),t.getId());
            return t;
        });
        doAnswer(inv->{jdbc.update("UPDATE refresh_token SET revoked=true WHERE user_id=?",(Long)inv.getArgument(0));return null;}).when(repo).revokeAllByUser(anyLong());
        RefreshTokenService target=new RefreshTokenService(repo,jwt,sessions);ReflectionTestUtils.setField(target,"refreshTokenValidityInMilliseconds",604800000L);tokens=proxy(target);
    }
    @SuppressWarnings("unchecked") <T>T proxy(T target) {ProxyFactory p=new ProxyFactory(target);p.setProxyTargetClass(true);p.addAdvice(new TransactionInterceptor(tx,new AnnotationTransactionAttributeSource()));return (T)p.getProxy();}
    @AfterEach void cleanup(){if(!mysql&&admin!=null&&schema!=null&&schema.matches("session_test_[a-f0-9]{32}"))admin.execute("DROP SCHEMA "+schema+" CASCADE");}
    @Test void oneDeviceLogoutImmediatelyRejectsAccessAndRefreshButNotOtherDevice(){AuthTokens a=tokens.createTokenPair(user),b=tokens.createTokenPair(user);UUID id=jwt.getSessionId(a.getAccessToken());sessions.revoke(id,1L);assertThrows(CustomException.class,()->jwt.getAuthentication(a.getAccessToken()));assertThrows(CustomException.class,()->tokens.rotate(a.getRefreshToken()));assertNotNull(jwt.getAuthentication(b.getAccessToken()));assertNotNull(tokens.rotate(b.getRefreshToken()));}
    @Test void guessedOtherUsersSessionCannotBeReadOrRevoked(){UUID other=sessions.create(2L,new Date(System.currentTimeMillis()+60000));CustomException ex=assertThrows(CustomException.class,()->sessions.revoke(other,1L));assertEquals(404,ex.getHttpStatus().value());assertTrue(sessions.list(1L,null).isEmpty());sessions.assertActive(other,2L);}
    @Test void rotationPreservesSessionIdentityAndOneDeviceRow(){AuthTokens first=tokens.createTokenPair(user);AuthTokens rotated=tokens.rotate(first.getRefreshToken());assertEquals(jwt.getSessionId(first.getAccessToken()),jwt.getSessionId(rotated.getAccessToken()));assertEquals(1,sessions.list(1L,null).size());}
    @Test void refreshReuseRevokesOnlyItsLoginFamily(){AuthTokens a=tokens.createTokenPair(user),b=tokens.createTokenPair(user);AuthTokens next=tokens.rotate(a.getRefreshToken());assertThrows(CustomException.class,()->tokens.rotate(a.getRefreshToken()));assertThrows(CustomException.class,()->jwt.getAuthentication(next.getAccessToken()));assertNotNull(jwt.getAuthentication(b.getAccessToken()));}
    @Test void logoutAllAllowsAnImmediateNewLoginWithoutSecondPrecisionFalseRejection(){AuthTokens a=tokens.createTokenPair(user),b=tokens.createTokenPair(user);sessions.revokeAll(1L);user.setSessionInvalidatedAt(jdbc.queryForObject("SELECT session_invalidated_at FROM own_user WHERE id=1",Timestamp.class));assertThrows(CustomException.class,()->jwt.getAuthentication(a.getAccessToken()));assertThrows(CustomException.class,()->jwt.getAuthentication(b.getAccessToken()));AuthTokens fresh=tokens.createTokenPair(user);assertNotNull(jwt.getAuthentication(fresh.getAccessToken()));}
    @Test void databaseDisableInvalidatesAccessEvenIfUserObjectIsStale(){AuthTokens a=tokens.createTokenPair(user);jdbc.update("UPDATE own_user SET enabled=false WHERE id=1");assertThrows(CustomException.class,()->jwt.getAuthentication(a.getAccessToken()));assertThrows(CustomException.class,()->tokens.createTokenPair(user));}
    @Test void sessionAndRefreshAreRolledBackTogether(){assertThrows(IllegalStateException.class,()->new TransactionTemplate(tx).execute(status->{tokens.createTokenPair(user);throw new IllegalStateException("injected");}));assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM auth_session",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM refresh_token",Integer.class));}
    @Test void expiryCannotBeExtendedAfterItHasExpired(){AuthTokens a=tokens.createTokenPair(user);UUID id=jwt.getSessionId(a.getAccessToken());jdbc.update("UPDATE auth_session SET expires_at=? WHERE id=?",Timestamp.from(java.time.Instant.now().minusSeconds(1)),id);assertThrows(CustomException.class,()->tokens.rotate(a.getRefreshToken()));assertThrows(CustomException.class,()->jwt.getAuthentication(a.getAccessToken()));}
    @Test void concurrentLogoutAndRefreshCannotResurrectRevokedFamily() throws Exception {AuthTokens a=tokens.createTokenPair(user);UUID id=jwt.getSessionId(a.getAccessToken());ExecutorService pool=Executors.newFixedThreadPool(2);CyclicBarrier barrier=new CyclicBarrier(2);try{Future<?> refresh=pool.submit(()->{try{barrier.await(5,TimeUnit.SECONDS);tokens.rotate(a.getRefreshToken());}catch(CustomException expected){}catch(Exception ex){throw new RuntimeException(ex);}});Future<?> logout=pool.submit(()->{try{barrier.await(5,TimeUnit.SECONDS);sessions.revokeAll(1L);}catch(Exception ex){throw new RuntimeException(ex);}});refresh.get(10,TimeUnit.SECONDS);logout.get(10,TimeUnit.SECONDS);assertThrows(CustomException.class,()->sessions.assertActive(id,1L));assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM refresh_token WHERE NOT revoked",Integer.class));}finally{pool.shutdownNow();}}
    @Test void repeatedRevokeIsIdempotent(){AuthTokens a=tokens.createTokenPair(user);UUID id=jwt.getSessionId(a.getAccessToken());sessions.revoke(id,1L);Timestamp first=jdbc.queryForObject("SELECT revoked_at FROM auth_session WHERE id=?",Timestamp.class,id);sessions.revoke(id,1L);assertEquals(first,jdbc.queryForObject("SELECT revoked_at FROM auth_session WHERE id=?",Timestamp.class,id));}
}
