package com.mrzym.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mrzym.blog.common.Num;
import com.mrzym.blog.entity.LikeEntity;
import com.mrzym.blog.mapper.ArticleMapper;
import com.mrzym.blog.mapper.LikeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 点赞记录，对齐原实现 service/like + controller/like。
 * 计数同步到 article/thumbs_up_times、talk|message/like_times、comment/thumbs_up。
 */
@Service
@RequiredArgsConstructor
public class LikeService {

    private final LikeMapper likeMapper;
    private final ArticleMapper articleMapper;

    public boolean addLike(Integer forId, Integer type, Integer userId, String ip) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        LikeEntity entity = new LikeEntity();
        entity.setForId(forId);
        entity.setType(type);
        entity.setUserId(userId);
        entity.setIp(ip);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        likeMapper.insert(entity);
        return true;
    }

    /** 返回删除条数；0 条时返回 null（对齐原实现 `return res ? res : null`） */
    public Integer cancelLike(Integer forId, Integer type, Integer userId, String ip) {
        LambdaQueryWrapper<LikeEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LikeEntity::getForId, forId).eq(LikeEntity::getType, type);
        if (userId != null && userId != 0) {
            wrapper.eq(LikeEntity::getUserId, userId);
        } else {
            wrapper.eq(LikeEntity::getIp, ip);
        }
        int affected = likeMapper.delete(wrapper);
        return affected > 0 ? affected : null;
    }

    public boolean isLikedByUser(Integer forId, Integer type, Integer userId) {
        return likeMapper.selectCount(new LambdaQueryWrapper<LikeEntity>()
                .eq(LikeEntity::getForId, forId)
                .eq(LikeEntity::getType, type)
                .eq(LikeEntity::getUserId, userId)) > 0;
    }

    public boolean isLikedByIp(Integer forId, Integer type, String ip) {
        return likeMapper.selectCount(new LambdaQueryWrapper<LikeEntity>()
                .eq(LikeEntity::getForId, forId)
                .eq(LikeEntity::getType, type)
                .eq(LikeEntity::getIp, ip)) > 0;
    }

    /** 同步业务表点赞数；返回受影响行数（原实现不校验返回值） */
    public void changeCounter(Integer type, Integer forId, boolean increase) {
        if (forId == null || type == null) {
            return;
        }
        switch (type) {
            case 1 -> {
                if (increase) {
                    articleMapper.incrementThumbsUp(forId);
                } else {
                    articleMapper.decrementThumbsUp(forId);
                }
            }
            case 2 -> {
                if (increase) {
                    likeMapper.increaseTalkLike(forId);
                } else {
                    likeMapper.decreaseTalkLike(forId);
                }
            }
            case 3 -> {
                if (increase) {
                    likeMapper.increaseMessageLike(forId);
                } else {
                    likeMapper.decreaseMessageLike(forId);
                }
            }
            case 4 -> {
                if (increase) {
                    likeMapper.increaseCommentLike(forId);
                } else {
                    likeMapper.decreaseCommentLike(forId);
                }
            }
            default -> {
            }
        }
    }

    public Integer parseId(Object value) {
        return Num.toInteger(value);
    }
}
