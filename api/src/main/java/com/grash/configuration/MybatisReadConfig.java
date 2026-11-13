package com.grash.configuration;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(name="enhancement.query.enabled",havingValue="true")
@MapperScan(basePackages="com.grash.readmodel.mapper",sqlSessionFactoryRef="querySqlSessionFactory")
public class MybatisReadConfig {
    @Bean
    public SqlSessionFactory querySqlSessionFactory(DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean factory=new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        MybatisConfiguration configuration=new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setDefaultStatementTimeout(3);
        factory.setConfiguration(configuration);
        MybatisPlusInterceptor interceptor=new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination=new PaginationInnerInterceptor(DbType.POSTGRE_SQL);
        pagination.setMaxLimit(100L);
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);
        factory.setPlugins(interceptor);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mybatis/query/*.xml"));
        return factory.getObject();
    }
}
