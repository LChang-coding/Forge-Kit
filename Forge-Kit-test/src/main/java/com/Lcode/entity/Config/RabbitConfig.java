package com.Lcode.entity.Config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;

public class RabbitConfig {
    public static final String USER_REGISTER_QUEUE = "user.register.queue";//定义存放消息的的队列
    public static final String USER_EXCHANGE = "user.exchange";//定义交换机
    public static final String REGISTER_ROUTING_KEY = "user.register";//定义路由键

    /**
     * 声明一个队列
     * 1. 队列名：USER_REGISTER_QUEUE (通常是一个常量字符串)
     * 2. 默认属性：
     * - durable: true (持久化，RabbitMQ重启后队列依然存在)
     * - exclusive: false (非排他，多个连接可以访问)
     * - autoDelete: false (不自动删除，即使没有消费者连接也不删除)
     */
    @Bean
    public Queue userRegisterQueue() {
        return new Queue(USER_REGISTER_QUEUE);
    }

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
                .bind(userRegisterQueue())   // 1. 指定要绑定的队列：这里调用上面定义的 bean 方法
                .to(userExchange())          // 2. 指定绑定到的交换机：这里指向上面定义的 DirectExchange
                .with(REGISTER_ROUTING_KEY); // 3. 指定路由键 (Routing Key)：
        //    在 Direct 模式下，只有当发送消息时指定的 key
        //    与这里的 REGISTER_ROUTING_KEY 完全一致，
        //    消息才会被投递到该队列。
    }
    //在 Spring 框架中，@Bean 是一个极其核心的注解。简单来说，它的作用是：告诉 Spring 容器，由我这个方法产生一个对象，并把这个对象交给 Spring 统一管理。
}