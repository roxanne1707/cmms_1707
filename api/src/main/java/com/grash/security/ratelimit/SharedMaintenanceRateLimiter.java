package com.grash.security.ratelimit;

import com.grash.security.ClientIpResolver;
import com.grash.security.CustomUserDetail;
import io.micrometer.core.instrument.Metrics;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/** Admission control only. Authorization, license checks and command idempotency remain in the domain. */
@Component
@ConditionalOnProperty(name="cmms.rate-limit.shared.enabled",havingValue="true")
public class SharedMaintenanceRateLimiter {
    private final StringRedisTemplate redis;
    private final SharedRateLimitProperties properties;
    private final ClientIpResolver addresses;
    private final DefaultRedisScript<List> script;
    public SharedMaintenanceRateLimiter(StringRedisTemplate redis,SharedRateLimitProperties properties) {
        properties.validate();this.redis=redis;this.properties=properties;
        addresses=new ClientIpResolver(properties.getTrustedProxies(),false);
        if(redis.getConnectionFactory() instanceof LettuceConnectionFactory lettuce) {
            var config=lettuce.getClientConfiguration();
            Duration connect=config.getClientOptions().map(o->o.getSocketOptions().getConnectTimeout()).orElse(Duration.ofSeconds(10));
            if(config.getCommandTimeout().compareTo(Duration.ofSeconds(2))>0||connect.compareTo(Duration.ofSeconds(2))>0)
                throw new IllegalArgumentException("Shared limiter requires Redis command/connect timeouts at most two seconds");
        }
        script=new DefaultRedisScript<>();script.setLocation(new ClassPathResource("redis/maintenance-rate-limit.lua"));script.setResultType(List.class);
    }
    public Optional<RateLimitDecision> evaluate(HttpServletRequest request,Authentication authentication) {
        if(!"POST".equals(request.getMethod())) return Optional.empty();
        String uri=request.getRequestURI();String context=request.getContextPath();
        if(context!=null&&!context.isEmpty()) uri=uri.substring(context.length());
        if("/requests".equals(uri)||"/requests/".equals(uri)) {
            if(authentication!=null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof CustomUserDetail detail
                    && detail.getUser().getCompany()!=null && detail.getUser().getId()!=null) {
                long company=detail.getUser().getCompany().getId();
                return Optional.of(consume("request", "company:"+company, List.of("all","user:"+identity(detail.getUser().getId().toString())),
                        List.of(properties.getOrganization(),properties.getSubject())));
            }
            return Optional.empty(); // Keep authentication rejection and legacy protection.
        }
        boolean portal=uri!=null&&uri.startsWith("/requests/portal/");
        boolean login="/auth/signin".equals(uri)||"/auth/signin-ldap".equals(uri);
        if(!portal&&!login)return Optional.empty();
        String ip=canonicalAddress(addresses.resolve(request));
        // IP gate is deliberately independent from the later, licensed portal quota.
        return Optional.of(consume(portal?"public-request":"login","ip:"+identity(ip),List.of("source"),List.of(properties.getAnonymous())));
    }
    public void requirePortal(long companyId,long portalId) {
        if(companyId<=0||portalId<=0)throw new IllegalArgumentException("Persisted portal identity required");
        var d=consume("portal","company:"+companyId,List.of("portal:"+portalId),List.of(properties.getOrganization()));
        if(!d.allowed())throw new RateLimitRejected(d);
    }
    RateLimitDecision consume(String policy,String scope,List<String> subjects,List<SharedRateLimitProperties.Quota> quotas) {
        List<String> keys=new ArrayList<>();List<String> args=new ArrayList<>();
        for(int i=0;i<subjects.size();i++) {
            keys.add(properties.getNamespace()+":rl:v1:{"+policy+":"+scope+"}:"+subjects.get(i));
            var q=quotas.get(i);args.add(Integer.toString(q.getBurst()));args.add(Long.toString(q.getRefillSeconds()*1000L));
            args.add(Integer.toString(q.getSustained()));args.add(Long.toString(q.getSustainedSeconds()*1000L));
        }
        long start=System.nanoTime();String outcome="unavailable";
        try {
            List<?> result=redis.execute(script,keys,args.toArray());
            if(result==null||result.size()!=2||!(result.get(0) instanceof Number allowed)||!(result.get(1) instanceof Number retry)
                    ||(allowed.longValue()!=0&&allowed.longValue()!=1)||retry.longValue()<0||retry.longValue()>86400) throw new IllegalStateException("Invalid limiter result");
            outcome=allowed.longValue()==1?"allowed":"limited";
            return new RateLimitDecision(allowed.longValue()==1?200:429,retry.longValue());
        } catch(RuntimeException unavailable) {
            return new RateLimitDecision(503,1); // No process-local fallback pretending to preserve a shared quota.
        } finally {
            Metrics.counter("cmms.rate.limit.decisions","policy",policy,"outcome",outcome).increment();
            Metrics.timer("cmms.rate.limit.duration","policy",policy).record(System.nanoTime()-start,java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }
    String identity(String value) {
        try {Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(properties.getIdentitySecret().getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.GeneralSecurityException impossible){throw new IllegalStateException("HMAC unavailable",impossible);}
    }
    static String canonicalAddress(String ip) {
        if(ip==null||ip.length()>45||!ip.matches("[0-9a-fA-F:.]+")||(!ip.contains(":")&&!ip.matches("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+")))return "unknown";
        try{return InetAddress.getByName(ip).getHostAddress();}catch(java.net.UnknownHostException invalid){return "unknown";}
    }
}
