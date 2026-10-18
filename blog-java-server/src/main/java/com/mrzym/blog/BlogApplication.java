package com.mrzym.blog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 个人博客后端（Spring Boot 版）。
 *
 * Mapper 扫描放在 MybatisPlusConfig 里，不放在这里：
 * 否则 @JsonTest 之类的测试切片会把 MapperFactoryBean 一起拉起来，
 * 报 "Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required"。
 */
@SpringBootApplication
public class BlogApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlogApplication.class, args);
    }
}
