## 第一步 构建springboot项目引入依赖

```java
<dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-amqp</artifactId>
        </dependency>
```

使用基于父版本springboot版本的amqp。

## 第二步 配置yml文件(开启confirm和return机制 为了后面的做准备 现在可以注释掉)

```java
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: admin
    password: password
    virtual-host: /

    # --- 1. 生产者可靠性配置（必须放在这里！） ---
    //# 开启 Confirm 机制
   // publisher-confirm-type: correlated
    //# 开启 Return 机制
   // publisher-returns: true

    # --- 2. 消费者配置 ---
    listener:
      simple:
        acknowledge-mode: manual
        concurrency: 3
        max-concurrency: 10
        prefetch: 1
```

## 第三步 配置mq的交换器以及队列 及绑定情况

```java
@Configuration
public class RabbitConfig {
 
    public static final String USER_REGISTER_QUEUE = "user.register.queue";
    public static final String USER_EXCHANGE = "user.exchange";
    public static final String REGISTER_ROUTING_KEY = "user.register";
 
    @Bean
    public Queue userRegisterQueue() {
        return new Queue(USER_REGISTER_QUEUE, true); // durable = true
    }
 
    @Bean
    public DirectExchange userExchange() {
        return new DirectExchange(USER_EXCHANGE);
    }
 
    @Bean
    public Binding binding() {
        return BindingBuilder.bind(userRegisterQueue())
                .to(userExchange())
                .with(REGISTER_ROUTING_KEY);
    }
}
```

## 第四步创建生产者和消费者

```java
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

```

```java
package com.Lcode.mq.consumer;

import com.Lcode.entity.Config.RabbitConfig;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;


@Component
public class UserConsumer {


    @RabbitListener(queues = RabbitConfig.USER_REGISTER_QUEUE)//监听队列
    @RabbitHandler
    public void registerConsumer(Integer userId) {
        System.out.println("用户 " + userId + " 注册成功，发送注册成功邮件！");
    }
}
```

运行结果

![4fcf5cd7-d64d-4fd7-8a1a-1c0bab5a47c2](C:\Users\jwjwc\AppData\Local\Temp\4fcf5cd7-d64d-4fd7-8a1a-1c0bab5a47c2.png)



## 第五步 进阶操作！！！ 配置死信队列 将死信交换机绑定到死信队列

```java
  public static final String DLX_EXCHANGE = "dlx.exchange";
    public static final String DLX_QUEUE = "dlx.queue";
    public static final String DLX_ROUTING_KEY = "dlx.routing";

    // 死信交换机和队列
    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE);
    }

    @Bean
    public Queue dlxQueue() {
        return QueueBuilder.durable(DLX_QUEUE).build();
    }

    @Bean
    public Binding dlxBinding() {
        return BindingBuilder.bind(dlxQueue()).to(dlxExchange()).with(DLX_ROUTING_KEY);
    }

    // 主队列绑定死信参数
    @Bean
    public Queue userDlxRegisterQueue() {
        return QueueBuilder.durable(USER_REGISTER_QUEUE) // 声明队列名并设置为持久化（磁盘存储）

                /**
                 * 【死信交换机配置 - DLX】
                 * 当消息在这个队列中变成“死信”时，会被转发到这个指定的交换机，而不是直接丢弃。
                 * 变成死信的条件：1.消息过期 2.队列满额 3.消费者拒绝且不重回队列
                 */
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)

                /**
                 * 【死信路由键配置 - DLK】
                 * 消息进入死信交换机后，将改用这个新的 Routing Key 进行路由。
                 * 这样可以方便地在死信交换机下，根据不同的 key 将错误消息分流到不同的处理队列。
                 */
                .withArgument("x-dead-letter-routing-key", DLX_ROUTING_KEY)

                /**
                 * 【消息生存时间 - TTL】
                 * 设置消息在队列中的最长驻留时间，单位：毫秒（此处为 10 秒）。
                 * 如果消息在 10 秒内没被消费者领走，就会自动触发“死信流程”，进入死信交换机。
                 */
                .withArgument("x-message-ttl", 10000)

                /**
                 * 【队列长度限制 - Max Length】
                 * 限制当前队列能容纳的消息总数。
                 * 当队列已存满 1000 条，第 1001 条进来时，最旧的消息会被踢出并投递到死信交换机。
                 * 作用：防止在大流量冲击下内存溢出，保证“较新”的消息优先处理。
                 */
                .withArgument("x-max-length", 1000)

                .build();
    }
```

## 第六步 将之前注释的yml复归 并在配置中写入两个机制的逻辑

```java
@Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        // 1. 实例化模板：它是 Spring 提供的操作 RabbitMQ 的核心工具类
        RabbitTemplate template = new RabbitTemplate(connectionFactory);

        /**
         * 【生产者确认机制 - ConfirmCallback】
         * 作用：确认消息是否到达了 Exchange（交换机）
         * 触发时机：当消息到达交换机后，Broker 异步回馈给生产者
         */
        template.setConfirmCallback((correlationData, ack, cause) -> {//correlationData 的核心作用：唯一标识
            String msgId = (correlationData != null) ? correlationData.getId() : null;//获取id的唯一标识
            // ack 为 true：Exchange 签收成功
            if (ack) {
                // 【修复点2】成功时必须清理 Map，防止内存泄漏
                if (msgId != null) retryMap.remove(msgId);
                // 可以在此处更新数据库中的消息状态为“已送达”

                System.out.println("【Confirm机制】消息已成功投递到 Exchange");
            } else {
                // 【关键逻辑】判断是不是我们自定义的“增强书包”

                if (correlationData instanceof EnhancedCorrelationData) {
                    EnhancedCorrelationData ecd = (EnhancedCorrelationData) correlationData;
                    // 1. 从书包里直接取出发送前存好的备份
                    Message originalMessage = ecd.getMessage();

                    int count = retryMap.getOrDefault(msgId, 0);
                    if (count < 3) {
                        retryMap.put(msgId, count + 1);
                        System.out.println("【Confirm机制】重试第 " + (count + 1) + " 次...");

                        // 2. 直接重发这个备份的消息体！ 为了演示方便 依旧发送失败试试
                        /*if (originalMessage != null) {
                            template.convertAndSend(RabbitConfig.USER_EXCHANGE, RabbitConfig.REGISTER_ROUTING_KEY, originalMessage, ecd);
                        }*/
                        if (originalMessage != null) {
                            template.convertAndSend("non-existent-exchange", RabbitConfig.REGISTER_ROUTING_KEY, originalMessage, ecd);
                        }
                    }
                else { // 2.2 重试次数耗尽 -> 转入死信队列
                    retryMap.remove(msgId);
                    System.err.println("【Confirm机制】重试 3 次失败，将错误信息转入死信队列...");

                    // 将失败信息发送到 RabbitConfig 定义的死信交换机
                    template.convertAndSend(RabbitConfig.DLX_EXCHANGE, RabbitConfig.DLX_ROUTING_KEY,
                            "消息发送ACK失败，ID: " + msgId + "，原因: " + cause);
            }
            }}
        });

        /**
         * 【失败退回机制 - ReturnsCallback】
         * 作用：当消息到了 Exchange，但 Exchange 根据 RoutingKey 找不到匹配的 Queue 时触发
         * 场景：通常是 RoutingKey 写错了，或者队列被意外删除了
         */
        template.setReturnsCallback(returned -> {
            System.err.println("【Return机制】消息路由失败！原因：" + returned.getReplyText());
            // 检查这个消息是不是已经发往 DLX 的
            if (RabbitConfig.DLX_EXCHANGE.equals(returned.getExchange())) {
                System.err.println("【Return严重错误】死信交换机也无法接收消息，请检查死信队列配置！");
                return; // 终止，不要再发了
            }
            System.err.println("【Return机制】正在将该消息转入死信队列...");

            // 路由失败时，returned 对象包含完整的消息体，直接转发到死信交换机
            template.convertAndSend(RabbitConfig.DLX_EXCHANGE, RabbitConfig.DLX_ROUTING_KEY, returned.getMessage());
            // 实战建议：由于交换机无法处理该消息，通常需要人工介入或检查路由键配置
        });

        /**
         * 【强制性标志 - Mandatory】
         * 作用：如果设置为 true，当消息无法路由到队列时，会触发上面的 ReturnsCallback。
         * 如果设置为 false（默认值），无法路由的消息会被 Broker 直接静默丢弃！
         */
        template.setMandatory(true);

        return template;
    }
```

## 第七步 进行用例测试

### 发送信息失败 即手动ack为false

```java
 /**
     * 场景 2：测试 Confirm 失败并重试 3 次进死信
     * 逻辑：通过发送到不存在的交换机（模拟投递失败）
     */
    @Test
    public void testConfirmRetryExhausted() throws InterruptedException {
        String msgId = "CONFIRM_FAIL_" + UUID.randomUUID();
        String content = "重试耗尽测试数据";

        System.out.println(">>> "+ msgId +"正在发送到不存在的交换机以触发投递失败...");

        // 故意使用一个不存在的交换机 "non-existent-exchange"
        rabbitTemplate.convertAndSend(
                "non-existent-exchange",
                RabbitConfig.REGISTER_ROUTING_KEY,
                content,
                new EnhancedCorrelationData(msgId, MessageBuilder.withBody(content.getBytes()).build())
        );

        // 等待 3 次重试逻辑执行完毕（取决于你的重试间隔，这里建议长一点）
        Thread.sleep(5000);
    }
```

该方法通过发送到无效交换器 使ack为false 结果如下

![3559cbae-597b-42a5-b509-a7744ae72336](C:\Users\jwjwc\AppData\Local\Temp\3559cbae-597b-42a5-b509-a7744ae72336.png)

重试三次进入死信队列。

### return机制测试用例 ：及交换机错误转发 导致调用return函数

```java
 /**
     * 场景 1：测试路由失败（ReturnsCallback 触发）
     * 逻辑：故意写错 RoutingKey，触发 Return 机制并转入死信
     */
    @Test
    public void testRoutingFailure() throws InterruptedException {
        String msgId = "ROUTE_FAIL_" + UUID.randomUUID();
        String content = "路由失败测试数据";

        System.out.println(">>> 正在发送路由键错误的消息...");

        // 故意使用一个不存在的路由键 "wrong.key"
        rabbitTemplate.convertAndSend(
                RabbitConfig.USER_EXCHANGE,
                "wrong.key",
                content
        );

        // 给一点异步回调处理的时间
        Thread.sleep(2000);
    }
```

通过无效的路由键来实现模拟

结果如下：



![98e8aef0-3a19-46af-b659-5868368597ba](C:\Users\jwjwc\AppData\Local\Temp\98e8aef0-3a19-46af-b659-5868368597ba.png)



### 基于上两个用例 输出死信队列内容

```java
/**
     * 场景 3：消费并输出死信队列中的所有消息
     * 逻辑：循环从死信队列拉取消息，直到队列为空
     */
    @Test
    public void printDeadLetterQueue() {
        System.out.println("--- 正在读取死信队列 [" + RabbitConfig.DLX_QUEUE + "] 内容 ---");
        int count = 0;

        while (true) {
            // receiveAndConvert 会尝试从队列拉取一条消息并转为对象
            // 如果队列为空，它会立即返回 null（非阻塞）
            Object message = rabbitTemplate.receiveAndConvert(RabbitConfig.DLX_QUEUE);

            if (message == null) {
                break;
            }

            count++;
            System.out.println("【死信消息 " + count + "】: " + message);
        }

        if (count == 0) {
            System.out.println(">>> 死信队列目前是空的。");
        } else {
            System.out.println(">>> 共计读取到 " + count + " 条死信消息。");
        }
    }
```

结果如下：

![84989cfb-4d6e-4ad7-bd87-075202470d5b](C:\Users\jwjwc\AppData\Local\Temp\84989cfb-4d6e-4ad7-bd87-075202470d5b.png)











