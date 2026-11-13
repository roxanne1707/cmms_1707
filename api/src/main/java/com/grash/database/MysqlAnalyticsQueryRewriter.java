package com.grash.database;
import org.springframework.data.jpa.repository.QueryRewriter;
import org.springframework.data.domain.Sort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
@Component
public class MysqlAnalyticsQueryRewriter implements QueryRewriter {
    private final DatabasePlatform database;
    private final ConcurrentHashMap<String,String> queries=new ConcurrentHashMap<>();
    private static final Pattern ID=Pattern.compile("/\\* cmms-query:([A-Za-z0-9-]+) \\*/");
    public MysqlAnalyticsQueryRewriter(DatabasePlatform database){this.database=database;}
    @Override public String rewrite(String query,Sort sort) {
        if(!database.isMysql())return query;
        var matcher=ID.matcher(query);
        if(!matcher.find())throw new IllegalArgumentException("Missing dialect query identifier");
        return queries.computeIfAbsent(matcher.group(1),id->{
            try(var in=new ClassPathResource("db/mysql/queries/"+id+".sql").getInputStream()){
                return new String(in.readAllBytes(),StandardCharsets.UTF_8);
            }catch(java.io.IOException ex){throw new IllegalStateException("Missing MySQL query: "+id,ex);}
        });
    }
}
