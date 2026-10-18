package com.mrzym.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mrzym.blog.common.CurrentPageResult;
import com.mrzym.blog.common.Num;
import com.mrzym.blog.dto.MessageForm;
import com.mrzym.blog.dto.MessageQuery;
import com.mrzym.blog.dto.MessageTagCount;
import com.mrzym.blog.dto.MessageView;
import com.mrzym.blog.entity.CommentEntity;
import com.mrzym.blog.entity.MessageEntity;
import com.mrzym.blog.entity.UserEntity;
import com.mrzym.blog.mapper.CommentMapper;
import com.mrzym.blog.mapper.MessageMapper;
import com.mrzym.blog.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 留言板，对齐原实现 service/message。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MessageMapper messageMapper;
    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final LikeService likeService;

    public boolean add(MessageForm form, String filteredMessage, String nickName) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        MessageEntity entity = new MessageEntity();
        entity.setMessage(filteredMessage);
        entity.setColor(form.getColor());
        entity.setFontSize(Num.toInteger(form.getFontSize()));
        entity.setFontWeight(Num.toInteger(form.getFontWeight()));
        entity.setBgColor(form.getBgColor());
        entity.setBgUrl(form.getBgUrl());
        entity.setUserId(Num.toInteger(form.getUserId()));
        entity.setNickName(nickName);
        entity.setTag(form.getTag());
        entity.setLikeTimes(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        messageMapper.insert(entity);
        return true;
    }

    /** 原实现返回 Sequelize 的数组（恒为真），这里保持 true */
    public boolean update(MessageForm form, String filteredMessage) {
        MessageEntity patch = new MessageEntity();
        patch.setId(Num.toInteger(form.getId()));
        patch.setMessage(filteredMessage);
        patch.setColor(form.getColor());
        patch.setFontSize(Num.toInteger(form.getFontSize()));
        patch.setFontWeight(Num.toInteger(form.getFontWeight()));
        patch.setBgColor(form.getBgColor());
        patch.setBgUrl(form.getBgUrl());
        patch.setTag(form.getTag());
        patch.setUpdatedAt(LocalDateTime.now().withNano(0));
        messageMapper.updateById(patch);
        return true;
    }

    public Integer delete(List<Integer> idList) {
        if (idList == null || idList.isEmpty()) {
            return null;
        }
        int affected = messageMapper.deleteBatchIds(idList);
        return affected > 0 ? affected : null;
    }

    public boolean like(Integer id) {
        return messageMapper.increaseLike(id) > 0;
    }

    public boolean cancelLike(Integer id) {
        return messageMapper.decreaseLike(id) > 0;
    }

    public CurrentPageResult<MessageView> list(MessageQuery query, String ip) {
        Object rawCurrent = query.getCurrent();
        Object rawSize = query.getSize();
        int pageNo = Math.max(Num.toInt(rawCurrent, 1), 1);
        int pageSize = Num.toInt(rawSize, 10);
        if (pageSize <= 0) {
            pageSize = 10;
        }

        LambdaQueryWrapper<MessageEntity> wrapper = new LambdaQueryWrapper<>();
        if (query.getTag() != null && !query.getTag().isBlank()) {
            wrapper.eq(MessageEntity::getTag, query.getTag());
        }
        if (query.getMessage() != null && !query.getMessage().isBlank()) {
            wrapper.like(MessageEntity::getMessage, query.getMessage());
        }
        LocalDateTime[] range = parseRange(query.getTime());
        if (range != null) {
            wrapper.between(MessageEntity::getCreatedAt, range[0], range[1]);
        }
        wrapper.orderByDesc(MessageEntity::getCreatedAt);

        Page<MessageEntity> page = messageMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<MessageView> list = toViews(page.getRecords());

        Integer userId = Num.toInteger(query.getUserId());
        boolean byUser = userId != null && userId != 0;
        for (MessageView view : list) {
            boolean liked = byUser
                    ? likeService.isLikedByUser(view.getId(), 3, userId)
                    : likeService.isLikedByIp(view.getId(), 3, ip);
            view.setIsLike(liked);
            view.setCommentTotal(commentMapper.selectCount(new LambdaQueryWrapper<CommentEntity>()
                    .eq(CommentEntity::getForId, view.getId())
                    .eq(CommentEntity::getType, 3)));
        }

        return CurrentPageResult.of(
                rawCurrent != null ? rawCurrent : pageNo,
                rawSize != null ? rawSize : pageSize,
                page.getTotal(),
                list);
    }

    /** 全部留言：{list, total} */
    public Map<String, Object> getAll() {
        LambdaQueryWrapper<MessageEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(MessageEntity::getCreatedAt);
        List<MessageEntity> rows = messageMapper.selectList(wrapper);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", toViews(rows));
        result.put("total", rows.size());
        return result;
    }

    public List<MessageTagCount> hotTags() {
        return messageMapper.selectHotTags();
    }

    /** 游客昵称：游客 + 5 位随机数字/字母 */
    public String randomGuestNickname() {
        String chars = "0123456789abcdefghijklmnopqrstuvwxyz";
        StringBuilder sb = new StringBuilder("游客");
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 5; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    // ==================== 组装 ====================

    private List<MessageView> toViews(List<MessageEntity> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<Integer> userIds = new LinkedHashSet<>();
        for (MessageEntity row : rows) {
            if (row.getUserId() != null) {
                userIds.add(row.getUserId());
            }
        }
        Map<Integer, UserEntity> users = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (UserEntity user : userMapper.selectBatchIds(userIds)) {
                users.put(user.getId(), user);
            }
        }

        List<MessageView> list = new ArrayList<>(rows.size());
        for (MessageEntity row : rows) {
            MessageView view = new MessageView();
            BeanUtils.copyProperties(row, view);
            if (row.getUserId() != null) {
                UserEntity user = users.get(row.getUserId());
                if (user != null) {
                    view.setNickName(user.getNickName());
                    view.setAvatar(user.getAvatar());
                }
            } else {
                // 游客：昵称用留言里存的，头像固定空串
                view.setNickName(row.getNickName());
                view.setAvatar("");
            }
            list.add(view);
        }
        return list;
    }

    private LocalDateTime[] parseRange(Object raw) {
        if (!(raw instanceof List<?> list) || list.size() != 2) {
            return null;
        }
        LocalDateTime start = parseDateTime(list.get(0));
        LocalDateTime end = parseDateTime(list.get(1));
        if (start == null || end == null) {
            return null;
        }
        return new LocalDateTime[]{start, end};
    }

    private LocalDateTime parseDateTime(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text, DATE_TIME);
        } catch (Exception e) {
            log.warn("无法解析留言查询时间: {}", text);
            return null;
        }
    }
}
