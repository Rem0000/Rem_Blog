package com.mrzym.blog.controller;

import com.mrzym.blog.common.BizException;
import com.mrzym.blog.common.ErrorCode;
import com.mrzym.blog.common.Result;
import com.mrzym.blog.dto.ArticleListResult;
import com.mrzym.blog.service.ArticleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/article")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    /**
     * 首页文章列表。分页走路径参数（保持原契约），响应里仍然是 current/size。
     */
    @GetMapping("/blogHomeGetArticleList/{current}/{size}")
    public Result<ArticleListResult> blogHomeGetArticleList(@PathVariable int current,
                                                            @PathVariable int size) {
        try {
            return Result.ok("获取文章列表成功", articleService.getHomeList(current, size));
        } catch (Exception e) {
            log.error("获取文章列表失败", e);
            throw new BizException(ErrorCode.ARTICLE, "获取文章列表失败");
        }
    }
}
