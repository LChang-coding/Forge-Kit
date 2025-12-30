package com.Lcode;


import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;

/**
 * 自定义关联数据类
 * 继承 CorrelationData，增加 message 字段用来暂存原始消息内容
 */
public class EnhancedCorrelationData extends CorrelationData {

    // 用来存放发送时的原始消息体
    private Message message;

    // 构造方法：必须传入 ID
    public EnhancedCorrelationData(String id) {
        super(id);
    }

    // 重载构造方法：传入 ID 和 消息体
    public EnhancedCorrelationData(String id, Message message) {
        super(id);
        this.message = message;
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }
}
