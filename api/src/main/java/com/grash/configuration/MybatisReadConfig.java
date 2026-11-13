package com.grash.configuration;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import javax.sql.DataSource;

@Configuration
@MapperScan(basePackages={"com.grash.readmodel.mapper","com.grash.writemodel.mapper"},sqlSessionFactoryRef="querySqlSessionFactory")
public class MybatisReadConfig {
    @Bean
    public SqlSessionFactory querySqlSessionFactory(DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean factory=new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        org.apache.ibatis.mapping.VendorDatabaseIdProvider vendor=new org.apache.ibatis.mapping.VendorDatabaseIdProvider();
        java.util.Properties vendors=new java.util.Properties(); vendors.setProperty("MySQL","mysql"); vendors.setProperty("PostgreSQL","postgresql");
        vendor.setProperties(vendors); factory.setDatabaseIdProvider(vendor);
        MybatisConfiguration configuration=new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.getTypeHandlerRegistry().register(java.util.UUID.class,com.grash.readmodel.typehandler.PortableUuidTypeHandler.class);
        configuration.setDefaultStatementTimeout(3);
        factory.setConfiguration(configuration);
        MybatisPlusInterceptor interceptor=new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination=new PaginationInnerInterceptor();
        pagination.setMaxLimit(100L);
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);
        factory.setPlugins(interceptor);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mybatis/query/*.xml"));
        return factory.getObject();
    }
}
