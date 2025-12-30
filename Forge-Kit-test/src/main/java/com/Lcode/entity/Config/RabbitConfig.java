package com.Lcode.entity.Config;

import com.Lcode.EnhancedCorrelationData;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class RabbitConfig {
    public static final String USER_REGISTER_QUEUE = "user.register.queue";//定义存放消息的的队列
    public static final String USER_EXCHANGE = "user.exchange";//定义交换机
    public static final String REGISTER_ROUTING_KEY = "user.register";//定义路由键

    // 用于记录消息重试次数的容器：Key=消息ID, Value=已重试次数
    private final Map<String, Integer> retryMap = new ConcurrentHashMap<>();
    /**
     * 声明一个队列
     * 1. 队列名：USER_REGISTER_QUEUE (通常是一个常量字符串)
     * 2. 默认属性：
     * - durable: true (持久化，RabbitMQ重启后队列依然存在)
     * - exclusive: false (非排他，多个连接可以访问)
     * - autoDelete: false (不自动删除，即使没有消费者连接也不删除)
     */
   /* @Bean
    public Queue userRegisterQueue() {
        return new Queue(USER_REGISTER_QUEUE);
    }*/

    /**
     * 声明一个直连交换机 (Direct Exchange)
     * 直连模式：消息中的 Routing Key 与 绑定时的 Binding Key 完全一致时，
     * 交换机才会将消息路由到对应的队列中。
     * * 场景：非常适合精准推送，如“用户注册”这个特定事件。
     * 因为使用的是 DirectExchange，所以这个绑定关系是非常严苛的。
     */
    @Bean
    public DirectExchange userExchange() {
        return new DirectExchange(USER_EXCHANGE);
    }

    @Bean
    public Binding binding() {
        return BindingBuilder
                .bind(userDlxRegisterQueue())   // 1. 指定要绑定的队列：这里调用上面定义的 bean 方法
                .to(userExchange())          // 2. 指定绑定到的交换机：这里指向上面定义的 DirectExchange
                .with(REGISTER_ROUTING_KEY); // 3. 指定路由键 (Routing Key)：
        //    在 Direct 模式下，只有当发送消息时指定的 key
        //    与这里的 REGISTER_ROUTING_KEY 完全一致，
        //    消息才会被投递到该队列。
    }
    //在 Spring 框架中，@Bean 是一个极其核心的注解。简单来说，它的作用是：告诉 Spring 容器，由我这个方法产生一个对象，并把这个对象交给 Spring 统一管理。
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

}