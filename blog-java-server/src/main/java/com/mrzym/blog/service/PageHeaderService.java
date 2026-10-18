package com.mrzym.blog.service;

import com.mrzym.blog.dto.PageHeaderItem;
import com.mrzym.blog.mapper.PageHeaderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PageHeaderService {

    private final PageHeaderMapper pageHeaderMapper;

    public List<PageHeaderItem> getAll() {
        return pageHeaderMapper.selectAll();
    }
}
