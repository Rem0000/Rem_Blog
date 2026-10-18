package com.mrzym.blog.controller;

import com.mrzym.blog.common.BizException;
import com.mrzym.blog.common.ErrorCode;
import com.mrzym.blog.common.Result;
import com.mrzym.blog.dto.StatisticResult;
import com.mrzym.blog.service.StatisticService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class StatisticController {

    private final StatisticService statisticService;

    /** 首页统计：文章/标签/分类/用户 四个总数 */
    @GetMapping({"/statistic", "/statistic/"})
    public Result<StatisticResult> getStatistic() {
        try {
            return Result.ok("获取数据统计成功", statisticService.getStatistic());
        } catch (Exception e) {
            log.error("获取数据统计失败", e);
            throw new BizException(ErrorCode.STATISTIC, "获取数据统计失败");
        }
    }
}
