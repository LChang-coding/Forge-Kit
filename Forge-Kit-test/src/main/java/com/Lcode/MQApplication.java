package com.Lcode;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MQApplication {
    public static void main(String[] args) {
        SpringApplication.run(MQApplication.class,  args);
        System.out.println("🚀 实战项目启动成功！RabbitMQ 消费者已就绪...");}
}
