package com.aeromq.protocol;

import java.util.Map;

/**
 * 临时的AeroProtocol类，用于解决依赖问题
 */
public class AeroProtocol {

    public static final String PROTOCOL_VERSION = "1.0";
    public static final int DEFAULT_PORT = 9000;

    /**
     * 状态码常量
     */
    public static class StatusCodes {
        public static final int SUCCESS = 200;
        public static final int ERROR = 500;
        public static final int NOT_FOUND = 404;
        public static final int UNAUTHORIZED = 401;
    }

    /**
     * 协议消息
     */
    public static class ProtocolMessage {
        private String version;
        private String command;
        private long requestId;
        private long timestamp;
        private String clientId;
        private Map<String, Object> headers;
        private byte[] payload;

        public ProtocolMessage() {}

        public ProtocolMessage(String version, String command, long requestId,
                             long timestamp, String clientId,
                             Map<String, Object> headers, byte[] payload) {
            this.version = version;
            this.command = command;
            this.requestId = requestId;
            this.timestamp = timestamp;
            this.clientId = clientId;
            this.headers = headers;
            this.payload = payload;
        }

        // Getters and setters
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }

        public String getCommand() { return command; }
        public void setCommand(String command) { this.command = command; }

        public long getRequestId() { return requestId; }
        public void setRequestId(long requestId) { this.requestId = requestId; }

        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }

        public Map<String, Object> getHeaders() { return headers; }
        public void setHeaders(Map<String, Object> headers) { this.headers = headers; }

        public byte[] getPayload() { return payload; }
        public void setPayload(byte[] payload) { this.payload = payload; }
    }

    /**
     * 协议响应
     */
    public static class ProtocolResponse {
        private String version;
        private long requestId;
        private long timestamp;
        private int statusCode;
        private String statusMessage;
        private Map<String, Object> data;

        public ProtocolResponse() {}

        public ProtocolResponse(String version, long requestId, long timestamp,
                              int statusCode, String statusMessage,
                              Map<String, Object> data) {
            this.version = version;
            this.requestId = requestId;
            this.timestamp = timestamp;
            this.statusCode = statusCode;
            this.statusMessage = statusMessage;
            this.data = data;
        }

        // Getters and setters
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }

        public long getRequestId() { return requestId; }
        public void setRequestId(long requestId) { this.requestId = requestId; }

        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

        public int getStatusCode() { return statusCode; }
        public void setStatusCode(int statusCode) { this.statusCode = statusCode; }

        public String getStatusMessage() { return statusMessage; }
        public void setStatusMessage(String statusMessage) { this.statusMessage = statusMessage; }

        public Map<String, Object> getData() { return data; }
        public void setData(Map<String, Object> data) { this.data = data; }
    }
}
