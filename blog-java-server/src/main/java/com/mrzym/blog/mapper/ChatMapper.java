package com.mrzym.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mrzym.blog.entity.ChatEntity;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ChatMapper extends BaseMapper<ChatEntity> {

    /** 对应原实现的 destroy({where:{}, truncate:true})：清空并重置自增 */
    @Update("TRUNCATE TABLE blog_chat")
    void truncate();

    @Select("SELECT MAX(id) FROM blog_chat")
    Integer selectMaxId();
}
