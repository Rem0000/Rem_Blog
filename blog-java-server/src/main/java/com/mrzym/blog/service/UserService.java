package com.mrzym.blog.service;

import com.mrzym.blog.dto.UserInfo;
import com.mrzym.blog.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    /** 查不到时返回 null，与原实现一致（success + result:null，不报错） */
    public UserInfo getInfoById(Integer id) {
        return userMapper.selectInfoById(id);
    }
}
