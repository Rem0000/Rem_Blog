package com.mrzym.blog.service;

import com.mrzym.blog.dto.StatisticResult;
import com.mrzym.blog.mapper.StatisticMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StatisticService {

    private final StatisticMapper statisticMapper;

    public StatisticResult getStatistic() {
        return statisticMapper.selectStatistic();
    }
}
