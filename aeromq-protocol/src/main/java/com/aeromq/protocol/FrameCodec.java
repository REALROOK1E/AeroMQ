package com.aeromq.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * Frame codec implementation following AeroMQ protocol specification
 * 
 * Frame structure:
 * [4B frameLength][1B flags][nB headerJson][optional payload bytes]
 * 
 * - frameLength (int32): Total bytes from flags to frame end (excluding these 4 bytes)
 * - flags (byte): bitfield, bit0: hasPayload, bit1: isResponse, bit2..reserved
 * - headerJson (UTF-8): JSON object with requestId (long), command (string), etc.
 * - payload: Optional binary data when hasPayload=1
 */
public final class FrameCodec {
    
    private static final Logger logger = LoggerFactory.getLogger(FrameCodec.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // Flag bits
    public static final byte FLAG_HAS_PAYLOAD = 0x01;
    public static final byte FLAG_IS_RESPONSE = 0x02;
    
    /**
     * Request frame structure
     */
    public static class Request {
        private long requestId;
        private String command;
        private String topic;
        private String shardKey;
        private int payloadLen;
        private byte[] payload;
        
        // Constructors, getters, setters
        public Request() {}
        
        public Request(long requestId, String command, String topic, String shardKey, byte[] payload) {
            this.requestId = requestId;
            this.command = command;
            this.topic = topic;
            this.shardKey = shardKey;
            this.payload = payload;
            this.payloadLen = payload != null ? payload.length : 0;
        }
        
        // Getters and setters
        public long getRequestId() { return requestId; }
        public void setRequestId(long requestId) { this.requestId = requestId; }
        
        public String getCommand() { return command; }
        public void setCommand(String command) { this.command = command; }
        
        public String getTopic() { return topic; }
        public void setTopic(String topic) { this.topic = topic; }
        
        public String getShardKey() { return shardKey; }
        public void setShardKey(String shardKey) { this.shardKey = shardKey; }
        
        public int getPayloadLen() { return payloadLen; }
        public void setPayloadLen(int payloadLen) { this.payloadLen = payloadLen; }
        
        public byte[] getPayload() { return payload; }
        public void setPayload(byte[] payload) { 
            this.payload = payload;
            this.payloadLen = payload != null ? payload.length : 0;
        }
    }
    
    /**
     * Response frame structure
     */
    public static class Response {
        private long requestId;
        private String command = "response";
        private String status;
        private String error;
        private byte[] payload;
        
        // Constructors
        public Response() {}
        
        public Response(long requestId, String status, String error, byte[] payload) {
            this.requestId = requestId;
            this.status = status;
            this.error = error;
            this.payload = payload;
        }
        
        // Getters and setters
        public long getRequestId() { return requestId; }
        public void setRequestId(long requestId) { this.requestId = requestId; }
        
        public String getCommand() { return command; }
        public void setCommand(String command) { this.command = command; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        
        public byte[] getPayload() { return payload; }
        public void setPayload(byte[] payload) { this.payload = payload; }
    }
    
    /**
     * Encode request to ByteBuf
     */
    public static ByteBuf encodeRequest(Request req, ByteBufAllocator alloc) {
        try {
            // Serialize header to JSON
            byte[] headerBytes = objectMapper.writeValueAsBytes(req);
            
            // Calculate frame size
            byte flags = 0;
            if (req.getPayload() != null && req.getPayload().length > 0) {
                flags |= FLAG_HAS_PAYLOAD;
            }
            
            int frameLength = 1 + headerBytes.length; // flags + header
            if ((flags & FLAG_HAS_PAYLOAD) != 0) {
                frameLength += req.getPayload().length;
            }
            
            // Allocate buffer
            ByteBuf buffer = alloc.buffer(4 + frameLength);
            
            // Write frame
            buffer.writeInt(frameLength);
            buffer.writeByte(flags);
            buffer.writeBytes(headerBytes);
            
            if ((flags & FLAG_HAS_PAYLOAD) != 0) {
                buffer.writeBytes(req.getPayload());
            }
            
            return buffer;
            
        } catch (Exception e) {
            logger.error("Failed to encode request", e);
            throw new RuntimeException("Frame encoding failed", e);
        }
    }
    
    /**
     * Encode response to ByteBuf
     */
    public static ByteBuf encodeResponse(Response resp, ByteBufAllocator alloc) {
        try {
            // Serialize header to JSON
            byte[] headerBytes = objectMapper.writeValueAsBytes(resp);
            
            // Calculate frame size
            byte flags = FLAG_IS_RESPONSE;
            if (resp.getPayload() != null && resp.getPayload().length > 0) {
                flags |= FLAG_HAS_PAYLOAD;
            }
            
            int frameLength = 1 + headerBytes.length; // flags + header
            if ((flags & FLAG_HAS_PAYLOAD) != 0) {
                frameLength += resp.getPayload().length;
            }
            
            // Allocate buffer
            ByteBuf buffer = alloc.buffer(4 + frameLength);
            
            // Write frame
            buffer.writeInt(frameLength);
            buffer.writeByte(flags);
            buffer.writeBytes(headerBytes);
            
            if ((flags & FLAG_HAS_PAYLOAD) != 0) {
                buffer.writeBytes(resp.getPayload());
            }
            
            return buffer;
            
        } catch (Exception e) {
            logger.error("Failed to encode response", e);
            throw new RuntimeException("Frame encoding failed", e);
        }
    }
    
    /**
     * Decode request from ByteBuf
     */
    public static Request decodeRequest(ByteBuf frame) {
        try {
            // Read frame length (already consumed by LengthFieldBasedFrameDecoder)
            // frame should contain: [1B flags][nB headerJson][optional payload]
            
            if (frame.readableBytes() < 1) {
                throw new IllegalArgumentException("Frame too short");
            }
            
            // Read flags
            byte flags = frame.readByte();
            boolean hasPayload = (flags & FLAG_HAS_PAYLOAD) != 0;
            boolean isResponse = (flags & FLAG_IS_RESPONSE) != 0;
            
            if (isResponse) {
                throw new IllegalArgumentException("Expected request, got response");
            }
            
            // Determine header length
            int headerLength = frame.readableBytes();
            if (hasPayload) {
                // We need to find where header ends and payload begins
                // For simplicity, we'll read until we can parse valid JSON
                // In production, consider including header length in frame
                headerLength = findHeaderEnd(frame);
            }
            
            // Read header JSON
            byte[] headerBytes = new byte[headerLength];
            frame.readBytes(headerBytes);
            
            Request request = objectMapper.readValue(headerBytes, Request.class);
            
            // Read payload if present
            if (hasPayload && frame.readableBytes() > 0) {
                byte[] payloadBytes = new byte[frame.readableBytes()];
                frame.readBytes(payloadBytes);
                request.setPayload(payloadBytes);
            }
            
            return request;
            
        } catch (Exception e) {
            logger.error("Failed to decode request", e);
            throw new RuntimeException("Frame decoding failed", e);
        }
    }
    
    /**
     * Decode response from ByteBuf
     */
    public static Response decodeResponse(ByteBuf frame) {
        try {
            if (frame.readableBytes() < 1) {
                throw new IllegalArgumentException("Frame too short");
            }
            
            // Read flags
            byte flags = frame.readByte();
            boolean hasPayload = (flags & FLAG_HAS_PAYLOAD) != 0;
            boolean isResponse = (flags & FLAG_IS_RESPONSE) != 0;
            
            if (!isResponse) {
                throw new IllegalArgumentException("Expected response, got request");
            }
            
            // Determine header length
            int headerLength = frame.readableBytes();
            if (hasPayload) {
                headerLength = findHeaderEnd(frame);
            }
            
            // Read header JSON
            byte[] headerBytes = new byte[headerLength];
            frame.readBytes(headerBytes);
            
            Response response = objectMapper.readValue(headerBytes, Response.class);
            
            // Read payload if present
            if (hasPayload && frame.readableBytes() > 0) {
                byte[] payloadBytes = new byte[frame.readableBytes()];
                frame.readBytes(payloadBytes);
                response.setPayload(payloadBytes);
            }
            
            return response;
            
        } catch (Exception e) {
            logger.error("Failed to decode response", e);
            throw new RuntimeException("Frame decoding failed", e);
        }
    }
    
    /**
     * Find the end of JSON header in the buffer
     * This is a simplified implementation - in production consider
     * including header length in the frame format
     */
    private static int findHeaderEnd(ByteBuf frame) {
        int readerIndex = frame.readerIndex();
        byte[] bytes = new byte[frame.readableBytes()];
        frame.getBytes(readerIndex, bytes);
        
        // Find the end of JSON by counting braces
        int braceCount = 0;
        boolean inString = false;
        boolean escaped = false;
        
        for (int i = 0; i < bytes.length; i++) {
            byte b = bytes[i];
            
            if (escaped) {
                escaped = false;
                continue;
            }
            
            if (b == '\\') {
                escaped = true;
                continue;
            }
            
            if (b == '"') {
                inString = !inString;
                continue;
            }
            
            if (!inString) {
                if (b == '{') {
                    braceCount++;
                } else if (b == '}') {
                    braceCount--;
                    if (braceCount == 0) {
                        return i + 1; // Include the closing brace
                    }
                }
            }
        }
        
        // If we can't find the end, assume no payload
        return frame.readableBytes();
    }
    
    /**
     * Create success response
     */
    public static Response createSuccessResponse(long requestId, byte[] payload) {
        return new Response(requestId, "ok", null, payload);
    }
    
    /**
     * Create error response
     */
    public static Response createErrorResponse(long requestId, String error) {
        return new Response(requestId, "error", error, null);
    }
}
