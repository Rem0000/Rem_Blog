package com.mrzym.blog.websocket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrzym.blog.common.Num;
import com.mrzym.blog.common.SensitiveWordFilter;
import com.mrzym.blog.dto.ChatForm;
import com.mrzym.blog.entity.ChatEntity;
import com.mrzym.blog.entity.UserEntity;
import com.mrzym.blog.mapper.UserMapper;
import com.mrzym.blog.service.ChatService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天 WebSocket，独立端口 8889，对齐原实现 utils/websocket.js。
 *
 * 协议（JSON）：
 *   {type:"init", user_id}              -> 上线，回广播 {type:"onlineList", list:[...]}
 *   {type:"message", user_id, content, content_type} -> 落库并广播（带 id/nick_name/avatar）
 *   {type:"revert", message_id}         -> 原样广播撤回
 *   {type:"offline", user_id}           -> 下线并广播在线列表
 *
 * 注意：Node 用 ws 库，这里用 Java-WebSocket，行为保持一致。
 */
@Slf4j
@Component
public class ChatWebSocketServer extends WebSocketServer {

    private static final DateTimeFormatter ISO_MILLIS = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC);

    private final ChatService chatService;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final List<Map<String, Object>> onlineList =
            Collections.synchronizedList(new ArrayList<>());

    public ChatWebSocketServer(@Value("${chat.ws-port:8889}") int port,
                               ChatService chatService,
                               UserMapper userMapper,
                               ObjectMapper objectMapper) {
        super(new InetSocketAddress(port));
        this.chatService = chatService;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void startServer() {
        start();
        log.info("websocket initialized on port: {}", getPort());
    }

    @PreDestroy
    public void stopServer() {
        try {
            stop(1000);
        } catch (Exception e) {
            log.warn("停止 WebSocket 失败", e);
        }
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        // 等客户端发 init 再绑定用户
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        Object attachment = conn.getAttachment();
        if (attachment instanceof Integer userId) {
            UserEntity user = userMapper.selectById(userId);
            if (user != null) {
                removeOnline(userId);
                broadcastOnlineList();
            }
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(message, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("忽略非法 WebSocket 消息: {}", message);
            return;
        }

        String type = payload.get("type") == null ? "" : String.valueOf(payload.get("type"));
        try {
            switch (type) {
                case "init" -> handleInit(conn, payload);
                case "message" -> handleMessage(payload);
                case "revert" -> {
                    if (payload.get("message_id") != null) {
                        broadcast(payload);
                    }
                }
                case "offline" -> handleOffline(payload);
                default -> {
                }
            }
        } catch (Exception e) {
            log.error("处理 WebSocket 消息失败: {}", message, e);
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        log.error("WebSocket 异常", ex);
    }

    @Override
    public void onStart() {
        log.info("WebSocket server started");
    }

    // ==================== 协议处理 ====================

    private void handleInit(WebSocket conn, Map<String, Object> payload) {
        Integer userId = Num.toInteger(payload.get("user_id"));
        if (userId == null || userId == 0) {
            broadcastOnlineList();
            return;
        }
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }
        conn.setAttachment(userId);
        payload.put("nick_name", user.getNickName());
        payload.put("avatar", user.getAvatar());
        upsertOnline(user);
        broadcastOnlineList();
    }

    private void handleMessage(Map<String, Object> payload) {
        String content = SensitiveWordFilter.filter(
                payload.get("content") == null ? "" : String.valueOf(payload.get("content")));
        payload.put("content", content);

        Integer userId = Num.toInteger(payload.get("user_id"));
        if (userId != null) {
            UserEntity user = userMapper.selectById(userId);
            if (user != null) {
                payload.put("nick_name", user.getNickName());
                payload.put("avatar", user.getAvatar());
            }
        }

        ChatForm form = new ChatForm();
        form.setContentType(payload.get("content_type") == null
                ? null : String.valueOf(payload.get("content_type")));
        form.setContent(content);
        form.setUserId(userId);
        ChatEntity chat = chatService.create(form);
        payload.put("id", chat.getId());

        broadcast(payload);
    }

    private void handleOffline(Map<String, Object> payload) {
        Integer userId = Num.toInteger(payload.get("user_id"));
        if (userId == null) {
            return;
        }
        UserEntity user = userMapper.selectById(userId);
        if (user != null) {
            removeOnline(userId);
            broadcastOnlineList();
        }
    }

    // ==================== 在线列表 ====================

    private void upsertOnline(UserEntity user) {
        synchronized (onlineList) {
            onlineList.removeIf(item -> user.getId().equals(item.get("user_id")));
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("user_id", user.getId());
            entry.put("nick_name", user.getNickName());
            entry.put("avatar", user.getAvatar());
            entry.put("createTime", ISO_MILLIS.format(Instant.now()));
            onlineList.add(entry);
        }
    }

    private void removeOnline(Integer userId) {
        synchronized (onlineList) {
            onlineList.removeIf(item -> userId.equals(item.get("user_id")));
        }
    }

    private void broadcastOnlineList() {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "onlineList");
        synchronized (onlineList) {
            message.put("list", new ArrayList<>(onlineList));
        }
        broadcast(message);
    }

    private void broadcast(Map<String, Object> payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("WebSocket 消息序列化失败", e);
            return;
        }
        for (WebSocket client : getConnections()) {
            if (client.isOpen()) {
                try {
                    client.send(json);
                } catch (Exception e) {
                    log.warn("WebSocket 发送失败", e);
                }
            }
        }
    }
}
