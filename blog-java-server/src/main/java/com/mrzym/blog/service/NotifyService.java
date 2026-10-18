package com.mrzym.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mrzym.blog.common.CurrentPageResult;
import com.mrzym.blog.common.Num;
import com.mrzym.blog.dto.NotifyQuery;
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
        LocalDateTime now = LocalDateTime.now().withNano(0);
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

    /** 已阅：is_view -> 2 */
    public boolean markViewed(Integer id) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        return notifyMapper.update(null, new LambdaUpdateWrapper<NotifyEntity>()
                .set(NotifyEntity::getIsView, 2)
                .set(NotifyEntity::getUpdatedAt, now)
                .eq(NotifyEntity::getId, id)) > 0;
    }

    /** 删除，返回条数 */
    public int deleteNotify(Integer id) {
        return notifyMapper.deleteById(id);
    }

    /** 分页：is_view 升序 + created_at 倒序 */
    public CurrentPageResult<NotifyEntity> getNotifyList(NotifyQuery query) {
        Object rawCurrent = query.getCurrent();
        Object rawSize = query.getSize();
        int pageNo = Math.max(Num.toInt(rawCurrent, 1), 1);
        int pageSize = Num.toInt(rawSize, 10);
        if (pageSize <= 0) {
            pageSize = 10;
        }

        LambdaQueryWrapper<NotifyEntity> wrapper = new LambdaQueryWrapper<>();
        Integer userId = Num.toInteger(query.getUserId());
        if (userId != null && userId != 0) {
            wrapper.eq(NotifyEntity::getUserId, userId);
        }
        wrapper.orderByAsc(NotifyEntity::getIsView).orderByDesc(NotifyEntity::getCreatedAt);

        Page<NotifyEntity> page = notifyMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return CurrentPageResult.of(
                rawCurrent != null ? rawCurrent : pageNo,
                rawSize != null ? rawSize : pageSize,
                page.getTotal(),
                page.getRecords());
    }
}
