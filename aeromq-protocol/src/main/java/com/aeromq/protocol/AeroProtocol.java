package com.aeromq.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * AeroMQ 协议定义
 * 定义了客户端和 Broker 之间的通信协议
 */
public class AeroProtocol {
    
    public static final String PROTOCOL_VERSION = "1.0";
    public static final int DEFAULT_PORT = 8888;
    
    // 协议头
    public static final String HEADER_VERSION = "version";
    public static final String HEADER_COMMAND = "command";
    public static final String HEADER_MESSAGE_ID = "messageId";
    public static final String HEADER_TIMESTAMP = "timestamp";
    public static final String HEADER_CLIENT_ID = "clientId";
    
    /**
     * 协议消息结构
     */
    public static class ProtocolMessage {
        private final String version;
        private final String command;
        private final long requestId;  // 从 String 改为 long 以提高性能
        private final long timestamp;
        private final String clientId;
        private final Map<String, Object> headers;
        private final byte[] payload;
        
        @JsonCreator
        public ProtocolMessage(
                @JsonProperty("version") String version,
                @JsonProperty("command") String command,
                @JsonProperty("requestId") long requestId,  // 更新类型
                @JsonProperty("timestamp") long timestamp,
                @JsonProperty("clientId") String clientId,
                @JsonProperty("headers") Map<String, Object> headers,
                @JsonProperty("payload") byte[] payload) {
            this.version = version;
            this.command = command;
            this.requestId = requestId;
            this.timestamp = timestamp;
            this.clientId = clientId;
            this.headers = headers;
            this.payload = payload;
        }
        
        // Getters 方法
        public String getVersion() { return version; }
        public String getCommand() { return command; }
        public long getRequestId() { return requestId; }  // 更新了返回类型
        public long getTimestamp() { return timestamp; }
        public String getClientId() { return clientId; }
        public Map<String, Object> getHeaders() { return headers; }
        public byte[] getPayload() { return payload; }
    }
    
    /**
     * 协议响应结构
     */
    public static class ProtocolResponse {
        private final long requestId;  // 从 String 改为 long
        private final int statusCode;
        private final String statusMessage;
        private final Map<String, Object> data;
        
        @JsonCreator
        public ProtocolResponse(
                @JsonProperty("requestId") long requestId,  // 更新类型
                @JsonProperty("statusCode") int statusCode,
                @JsonProperty("statusMessage") String statusMessage,
                @JsonProperty("data") Map<String, Object> data) {
            this.requestId = requestId;
            this.statusCode = statusCode;
            this.statusMessage = statusMessage;
            this.data = data;
        }
        
        // Getters 方法
        public long getRequestId() { return requestId; }  // 更新了返回类型
        public int getStatusCode() { return statusCode; }
        public String getStatusMessage() { return statusMessage; }
        public Map<String, Object> getData() { return data; }
    }
    
    /**
     * 状态码
     */
    public static class StatusCodes {
        public static final int SUCCESS = 200;
        public static final int ACCEPTED = 202;
        public static final int BAD_REQUEST = 400;
        public static final int UNAUTHORIZED = 401;
        public static final int NOT_FOUND = 404;
        public static final int INTERNAL_ERROR = 500;
        public static final int SERVICE_UNAVAILABLE = 503;
    }
    
    /**
     * 用于创建标准响应的工厂方法
     */
    public static ProtocolResponse success(long requestId, Map<String, Object> data) {
        return new ProtocolResponse(requestId, 200, "OK", data);
    }
    
    public static ProtocolResponse success(long requestId) {
        return success(requestId, null);
    }
    
    public static ProtocolResponse error(long requestId, int code, String message) {
        return new ProtocolResponse(requestId, code, message, null);
    }
    
    public static ProtocolResponse error(long requestId, String message) {
        return error(requestId, 500, message);
    }
}
