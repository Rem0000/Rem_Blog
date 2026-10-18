package com.mrzym.blog.service;

import com.mrzym.blog.entity.NotifyEntity;
import com.mrzym.blog.mapper.NotifyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 消息通知。原实现在 controller/notify 里有个 addNotify，内部 catch 掉异常，
 * 不影响主流程；友链申请就是调它。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyService {

    private final NotifyMapper notifyMapper;

    /**
     * 写一条通知。对应原实现 createNotify + 默认 isView=1。
     */
    public void addNotify(Integer userId, Integer type, Integer toId, String message) {
        LocalDateTime now = LocalDateTime.now();
        NotifyEntity entity = new NotifyEntity();
        entity.setUserId(userId);
        entity.setType(type);
        entity.setToId(toId);
        entity.setMessage(message);
        entity.setIsView(1);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        notifyMapper.insert(entity);
    }

    /**
     * 静默版：通知失败不影响主流程（对齐原实现 addNotify 的 try/catch）。
     */
    public void addNotifyQuietly(Integer userId, Integer type, Integer toId, String message) {
        try {
            addNotify(userId, type, toId, message);
        } catch (Exception e) {
            log.error("新增消息通知失败: {}", message, e);
        }
    }
}
