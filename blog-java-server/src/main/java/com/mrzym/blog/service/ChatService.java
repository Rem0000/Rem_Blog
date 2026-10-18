package com.mrzym.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mrzym.blog.common.Num;
import com.mrzym.blog.dto.ChatForm;
import com.mrzym.blog.dto.ChatPageResult;
import com.mrzym.blog.dto.ChatQuery;
import com.mrzym.blog.dto.ChatView;
import com.mrzym.blog.entity.ChatEntity;
import com.mrzym.blog.entity.UserEntity;
import com.mrzym.blog.mapper.ChatMapper;
import com.mrzym.blog.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 聊天，对齐原实现 service/chat。
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final DateTimeFormatter ISO_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    private final ChatMapper chatMapper;
    private final UserMapper userMapper;

    public ChatEntity create(ChatForm form) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        ChatEntity entity = new ChatEntity();
        entity.setUserId(Num.toInteger(form.getUserId()));
        entity.setContent(form.getContent());
        entity.setContentType(form.getContentType());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        chatMapper.insert(entity);
        return entity;
    }

    public void deleteAll() {
        chatMapper.truncate();
    }

    public int deleteOne(Integer id) {
        return chatMapper.deleteById(id);
    }

    public ChatEntity getOne(Integer id) {
        return chatMapper.selectById(id);
    }

    /**
     * 向上翻页：last_id 有值就取 id < last_id；否则从最新一条开始。
     * 返回的 size 是本次条数，list 升序（原实现先 desc 再 reverse）。
     */
    public ChatPageResult getChatList(ChatQuery query) {
        int size = Num.toInt(query.getSize(), 10);
        if (size <= 0) {
            size = 10;
        }
        Integer lastId = Num.toInteger(query.getLastId());

        Integer current;
        LambdaQueryWrapper<ChatEntity> wrapper = new LambdaQueryWrapper<>();
        if (lastId != null && lastId != 0) {
            wrapper.lt(ChatEntity::getId, lastId);
            current = lastId;
        } else {
            Integer maxId = chatMapper.selectMaxId();
            current = maxId;
            if (maxId != null) {
                wrapper.le(ChatEntity::getId, maxId);
            }
        }

        long total = 0;
        if (current != null) {
            LambdaQueryWrapper<ChatEntity> countWrapper = new LambdaQueryWrapper<>();
            if (lastId != null && lastId != 0) {
                countWrapper.lt(ChatEntity::getId, lastId);
            } else {
                countWrapper.le(ChatEntity::getId, current);
            }
            total = chatMapper.selectCount(countWrapper);
        }
        wrapper.orderByDesc(ChatEntity::getId).last("LIMIT " + size);
        List<ChatEntity> rows = chatMapper.selectList(wrapper);

        List<Object> list = new ArrayList<>(rows.size());
        for (ChatEntity row : rows) {
            if (row.getUserId() == null) {
                // 原实现 promise 返回 undefined，JSON 化后数组里是 null
                list.add(null);
                continue;
            }
            UserEntity user = userMapper.selectById(row.getUserId());
            ChatView view = new ChatView();
            view.setId(row.getId());
            view.setUserId(row.getUserId());
            view.setContent(row.getContent());
            view.setContentType(row.getContentType());
            view.setCreatedAt(toIsoUtc(row.getCreatedAt()));
            view.setUpdatedAt(toIsoUtc(row.getUpdatedAt()));
            if (user != null) {
                view.setNickName(user.getNickName());
                view.setAvatar(user.getAvatar());
            } else {
                view.setNickName("小黑子");
                view.setAvatar("");
            }
            list.add(view);
        }
        Collections.reverse(list);

        ChatPageResult result = new ChatPageResult();
        result.setCurrent(current);
        result.setSize(list.size());
        result.setTotal(total);
        result.setList(list);
        return result;
    }

    private String toIsoUtc(LocalDateTime time) {
        return time == null ? null : time.minusHours(8).format(ISO_UTC);
    }
}
