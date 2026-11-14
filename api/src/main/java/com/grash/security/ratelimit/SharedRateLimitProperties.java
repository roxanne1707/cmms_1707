package com.grash.security.ratelimit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix="cmms.rate-limit.shared")
public class SharedRateLimitProperties {
    private boolean enabled;
    private String namespace="";
    @lombok.ToString.Exclude
    private String identitySecret="";
    private String trustedProxies="";
    private Quota subject=new Quota(10,60,60,3600);
    private Quota organization=new Quota(120,60,600,3600);
    private Quota anonymous=new Quota(20,60,100,3600);
    @Data
    public static class Quota {
        private int burst, refillSeconds, sustained, sustainedSeconds;
        public Quota() { this(10,60,60,3600); }
        public Quota(int burst,int refillSeconds,int sustained,int sustainedSeconds) {
            this.burst=burst;this.refillSeconds=refillSeconds;this.sustained=sustained;this.sustainedSeconds=sustainedSeconds;
        }
        public void validate() {
            if(burst<1||burst>100000||sustained<1||sustained>100000||refillSeconds<1||refillSeconds>86400||sustainedSeconds<1||sustainedSeconds>86400)
                throw new IllegalArgumentException("Shared rate limit quotas are outside supported bounds");
        }
    }
    public void validate() {
        if(!namespace.matches("[A-Za-z0-9_-]{1,48}")||identitySecret.length()<32)
            throw new IllegalArgumentException("Shared rate limiting requires a namespace and a private identity secret of at least 32 characters");
        subject.validate();organization.validate();anonymous.validate();
    }
}
