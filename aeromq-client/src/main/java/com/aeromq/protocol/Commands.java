package com.aeromq.protocol;

/**
 * 临时的Commands类，定义协议命令常量
 */
public class Commands {

    // 连接相关命令
    public static final String CONNECT = "CONNECT";
    public static final String DISCONNECT = "DISCONNECT";
    public static final String PING = "PING";

    // 队列管理命令
    public static final String CREATE_QUEUE = "CREATE_QUEUE";
    public static final String DELETE_QUEUE = "DELETE_QUEUE";
    public static final String LIST_QUEUES = "LIST_QUEUES";

    // 消息相关命令
    public static final String SEND = "SEND";
    public static final String CONSUME = "CONSUME";
    public static final String SUBSCRIBE = "SUBSCRIBE";
    public static final String UNSUBSCRIBE = "UNSUBSCRIBE";
    public static final String ACK = "ACK";
    public static final String NACK = "NACK";
    public static final String SEND_BATCH = "SEND_BATCH";
}
