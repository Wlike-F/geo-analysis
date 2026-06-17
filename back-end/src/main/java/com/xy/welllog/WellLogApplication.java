package com.xy.welllog;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.xy.welllog.mapper")
public class WellLogApplication {

    public static void main(String[] args) {
        SpringApplication.run(WellLogApplication.class, args);
        System.out.println("(=^・^=) 地质测井数据分析系统 - 后端启动成功");
    }
}