package com.aipal.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis mapper scanning lives outside {@code AiPlatformApplication} so
 * {@code @WebMvcTest} slices are not forced to construct MapperFactoryBeans
 * (which need a SqlSessionFactory the slice does not provide).
 */
@Configuration
@MapperScan("com.aipal.**.mapper")
public class MybatisMapperConfig {
}
