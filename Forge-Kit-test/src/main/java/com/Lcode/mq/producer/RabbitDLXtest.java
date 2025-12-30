package com.Lcode.mq.producer;

import com.Lcode.EnhancedCorrelationData;
import com.Lcode.entity.Config.RabbitConfig;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@SpringBootTest
public class RabbitDLXtest {
    @Autowired
    private RabbitTemplate rabbitTemplate;

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
}
