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