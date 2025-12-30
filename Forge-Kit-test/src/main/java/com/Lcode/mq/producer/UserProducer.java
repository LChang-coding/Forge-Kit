package com.Lcode.mq.producer;

import com.Lcode.entity.Config.RabbitConfig;
import io.swagger.v3.oas.annotations.servers.Server;
import lombok.experimental.Accessors;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
@SpringBootTest
@Server
public class UserProducer {
    @Autowired
    private RabbitConfig rabbitConfig;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Test
    public void test() throws InterruptedException {
        // 场景 1：发送单条消息（常规测试）
        System.out.println("--- 场景 1：发送单条消息 ---");
        register(1001);

        // 场景 2：循环发送多条消息（模拟压力测试）
        System.out.println("\n--- 场景 2：循环发送 5 条消息 ---");
        for (int i = 1; i <= 5; i++) {
            register(2000 + i);
        }

        // 场景 3：模拟高并发发送（使用线程池）
        System.out.println("\n--- 场景 3：多线程并发发送消息 ---");
        ExecutorService executorService = Executors.newFixedThreadPool(3);
        for (int i = 0; i < 3; i++) {
            int finalI = i;
            executorService.submit(() -> {
                register(3000 + finalI);
            });
        }

        // 等待一会儿确保消息发出
        Thread.sleep(2000);
        executorService.shutdown();
    }
    private  void register(int userId){
        // 主流程：保存用户
        System.out.println("用户 " + userId + " 注册成功！");
        //异步发消息到MQ
        // 使用 RabbitTemplate 提供的 convertAndSend 方法发送消息
// 该方法会自动处理消息的序列化（默认将对象转为字节数组，若配置了 Jackson 则转为 JSON）
        rabbitTemplate.convertAndSend(
                // 1. 指定交换机名称 (Exchange)
                // 消息首先会被发送到这个交换机，由它决定把消息路由给哪个队列
                rabbitConfig.USER_EXCHANGE,

                // 2. 指定路由键 (Routing Key)
                // 交换机会根据这个 Key 寻找与其绑定的队列。在 Direct 模式下，这是“精确匹配”
                rabbitConfig.REGISTER_ROUTING_KEY,

                // 3. 消息主体 (Message Body)
                // 这里传递的是用户 ID。Spring 会将其包装成标准的 Message 对象进行传输
                userId
        );
}
}
