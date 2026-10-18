package com.mrzym.blog.controller;

import com.mrzym.blog.auth.RequireRole;
import com.mrzym.blog.common.BizException;
import com.mrzym.blog.common.CurrentPageResult;
import com.mrzym.blog.common.ErrorCode;
import com.mrzym.blog.common.Num;
import com.mrzym.blog.common.Result;
import com.mrzym.blog.dto.ArticleBrief;
import com.mrzym.blog.dto.ArticleCreateResult;
import com.mrzym.blog.dto.ArticleDetail;
import com.mrzym.blog.dto.ArticleForm;
import com.mrzym.blog.dto.ArticleListByIdQuery;
import com.mrzym.blog.dto.ArticleListItem;
import com.mrzym.blog.dto.ArticleListResult;
import com.mrzym.blog.dto.ArticleQuery;
import com.mrzym.blog.dto.ArticleTitleQuery;
import com.mrzym.blog.dto.ArticleUpdateResult;
import com.mrzym.blog.dto.HotArticleItem;
import com.mrzym.blog.dto.RecommendResult;
import com.mrzym.blog.dto.SearchResultItem;
import com.mrzym.blog.dto.TimelineResult;
import com.mrzym.blog.limit.RateLimit;
import com.mrzym.blog.service.ArticleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文章模块，对齐原实现 router/article.js + controller/article/index.js。
 */
@Slf4j
@RestController
@RequestMapping("/article")
@RequiredArgsConstructor
public class ArticleController {

    private static final String NUMBER = "^[0-9]+$";

    private final ArticleService articleService;

    // ==================== 前台/公共读 ====================

    /** 首页文章列表（路径分页，响应里 current/size 是字符串） */
    @GetMapping("/blogHomeGetArticleList/{current}/{size}")
    public Result<ArticleListResult> blogHomeGetArticleList(@PathVariable String current,
                                                            @PathVariable String size) {
        try {
            return Result.ok("获取文章列表成功", articleService.getHomeList(current, size));
        } catch (Exception e) {
            log.error("获取文章列表失败", e);
            throw new BizException(ErrorCode.ARTICLE, "获取文章列表失败");
        }
    }

    /** 时间轴（按年分组） */
    @GetMapping("/blogTimelineGetArticleList/{current}/{size}")
    public Result<TimelineResult> blogTimelineGetArticleList(@PathVariable String current,
                                                             @PathVariable String size) {
        try {
            return Result.ok("获取文章时间轴列表成功", articleService.getTimeline(current, size));
        } catch (Exception e) {
            log.error("获取文章时间轴列表失败", e);
            throw new BizException(ErrorCode.ARTICLE, "获取文章列表失败");
        }
    }

    /** 文章详情（每次访问 view_times +1，响应里返回加过之后的值） */
    @GetMapping("/getArticleById/{id}")
    public Result<ArticleDetail> getArticleById(@PathVariable Integer id) {
        try {
            ArticleDetail detail = articleService.getDetail(id);
            if (detail == null) {
                throw new BizException(ErrorCode.ARTICLE, "查询文章详情失败");
            }
            return Result.ok("查询文章详情成功", detail);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询文章详情失败", e);
            throw new BizException(ErrorCode.ARTICLE, "查询文章详情失败");
        }
    }

    /** 上一篇/下一篇 + 同标签推荐 */
    @GetMapping("/getRecommendArticleById/{id}")
    public Result<RecommendResult> getRecommendArticleById(@PathVariable Integer id) {
        try {
            return Result.ok("获取推荐文章成功", articleService.getRecommend(id));
        } catch (Exception e) {
            log.error("获取推荐文章失败", e);
            throw new BizException(ErrorCode.ARTICLE, "获取推荐文章失败");
        }
    }

    /** 全文搜索 */
    @GetMapping("/getArticleListByContent/{content}")
    public Result<List<SearchResultItem>> getArticleListByContent(@PathVariable String content) {
        try {
            return Result.ok("按照内容搜索文章成功", articleService.search(content));
        } catch (Exception e) {
            log.error("按照内容搜索文章失败", e);
            throw new BizException(ErrorCode.ARTICLE, "按照内容搜索文章失败");
        }
    }

    /** 热门文章（按访问量前 5） */
    @GetMapping("/getHotArticle")
    public Result<List<HotArticleItem>> getHotArticle() {
        try {
            return Result.ok("获取热门文章成功", articleService.getHot());
        } catch (Exception e) {
            log.error("获取热门文章失败", e);
            throw new BizException(ErrorCode.ARTICLE, "获取热门文章失败");
        }
    }

    /** 按标签分页 */
    @PostMapping("/getArticleListByTagId")
    public Result<CurrentPageResult<ArticleBrief>> getArticleListByTagId(
            @RequestBody ArticleListByIdQuery query) {
        Integer tagId = Num.toInteger(query.getId());
        if (tagId == null || tagId == 0) {
            throw new BizException(ErrorCode.ARTICLE, "标签id不能为空");
        }
        try {
            return Result.ok("根据标签获取文章列表成功",
                    articleService.getByTagId(query.getCurrent(), query.getSize(), tagId));
        } catch (Exception e) {
            log.error("根据标签获取文章列表失败", e);
            throw new BizException(ErrorCode.ARTICLE, "根据标签获取文章列表失败");
        }
    }

    /** 按分类分页 */
    @PostMapping("/getArticleListByCategoryId")
    public Result<CurrentPageResult<ArticleBrief>> getArticleListByCategoryId(
            @RequestBody ArticleListByIdQuery query) {
        Integer categoryId = Num.toInteger(query.getId());
        if (categoryId == null || categoryId == 0) {
            throw new BizException(ErrorCode.ARTICLE, "分类id不能为空");
        }
        try {
            return Result.ok("根据分类获取文章列表成功",
                    articleService.getByCategoryId(query.getCurrent(), query.getSize(), categoryId));
        } catch (Exception e) {
            log.error("根据分类获取文章列表失败", e);
            throw new BizException(ErrorCode.ARTICLE, "根据分类获取文章列表失败");
        }
    }

    // ==================== 后台 ====================

    /** 后台分页（登录即可，原实现只有 auth） */
    @PostMapping("/getArticleList")
    @RequireRole(RequireRole.Level.LOGIN)
    public Result<CurrentPageResult<ArticleListItem>> getArticleList(@RequestBody ArticleQuery query) {
        try {
            return Result.ok("查询文章成功", articleService.getAdminList(query));
        } catch (Exception e) {
            log.error("查询文章失败", e);
            throw new BizException(ErrorCode.ARTICLE, "查询文章失败");
        }
    }

    /** 标题查重：true 表示已存在 */
    @PostMapping("/titleExist")
    public Result<Boolean> titleExist(@RequestBody ArticleTitleQuery query) {
        try {
            return Result.ok("文章查询结果",
                    articleService.titleExists(query.getId(), query.getArticleTitle()));
        } catch (Exception e) {
            log.error("根据标题查询文章失败", e);
            throw new BizException(ErrorCode.ARTICLE, "根据标题查询文章失败");
        }
    }

    /** 新增文章 */
    @PostMapping("/add")
    @RequireRole(RequireRole.Level.ADMIN_NOT_SUPER)
    public Result<ArticleCreateResult> add(@RequestBody ArticleForm form) {
        verifyForm(form);
        if (articleService.titleExists(null, form.getArticleTitle())) {
            throw new BizException(ErrorCode.ARTICLE, "已存在相同的文章标题");
        }
        try {
            return Result.ok("新增文章成功", articleService.create(form));
        } catch (Exception e) {
            log.error("新增文章失败", e);
            throw new BizException(ErrorCode.ARTICLE, "新增文章失败");
        }
    }

    /** 修改文章 */
    @PutMapping("/update")
    @RequireRole(RequireRole.Level.ADMIN_NOT_SUPER)
    public Result<ArticleUpdateResult> update(@RequestBody ArticleForm form) {
        verifyForm(form);
        if (articleService.titleExists(form.getId(), form.getArticleTitle())) {
            throw new BizException(ErrorCode.ARTICLE, "已存在相同的文章标题");
        }
        try {
            return Result.ok("修改文章成功", articleService.update(form));
        } catch (Exception e) {
            log.error("修改文章失败", e);
            throw new BizException(ErrorCode.ARTICLE, "修改文章失败");
        }
    }

    /** 修改置顶 */
    @PutMapping("/updateTop/{id}/{is_top}")
    @RequireRole(RequireRole.Level.ADMIN_NOT_SUPER)
    public Result<Boolean> updateTop(@PathVariable String id, @PathVariable("is_top") String isTop) {
        verifyNumber(id, isTop);
        try {
            return Result.ok("修改文章置顶状态成功",
                    articleService.updateTop(Integer.valueOf(id), Integer.valueOf(isTop)));
        } catch (Exception e) {
            log.error("修改文章置顶状态失败", e);
            throw new BizException(ErrorCode.ARTICLE, "修改文章置顶状态失败");
        }
    }

    /** 删除：status=3 永久删除，否则进回收站（status=3） */
    @DeleteMapping("/delete/{id}/{status}")
    @RequireRole(RequireRole.Level.ADMIN_NOT_SUPER)
    public Result<Boolean> delete(@PathVariable String id, @PathVariable String status) {
        verifyNumber(id, status);
        try {
            return Result.ok("删除文章成功",
                    articleService.delete(Integer.valueOf(id), Integer.parseInt(status)));
        } catch (Exception e) {
            log.error("删除文章失败", e);
            throw new BizException(ErrorCode.ARTICLE, "删除文章失败");
        }
    }

    /** 恢复文章（回收站 -> 公开） */
    @PutMapping("/revert/{id}")
    @RequireRole(RequireRole.Level.ADMIN_NOT_SUPER)
    public Result<Boolean> revert(@PathVariable Integer id) {
        try {
            return Result.ok("恢复文章成功", articleService.revert(id));
        } catch (Exception e) {
            log.error("恢复文章失败", e);
            throw new BizException(ErrorCode.ARTICLE, "恢复文章失败");
        }
    }

    /** 切换公开/私密；请求里的 status 是当前状态 */
    @PutMapping("/isPublic/{id}/{status}")
    @RequireRole(RequireRole.Level.ADMIN_NOT_SUPER)
    public Result<Boolean> togglePublic(@PathVariable String id, @PathVariable String status) {
        verifyNumber(id, status);
        int currentStatus = Integer.parseInt(status);
        String message = currentStatus == 1 ? "隐藏文章" : "公开文章";
        try {
            return Result.ok(message + "成功",
                    articleService.togglePublic(Integer.valueOf(id), currentStatus));
        } catch (Exception e) {
            log.error("{}失败", message, e);
            throw new BizException(ErrorCode.ARTICLE, message + "失败");
        }
    }

    // ==================== 前台互动 ====================

    /** 文章点赞（1 分钟 10 次） */
    @PutMapping("/like/{id}")
    @RateLimit(key = "article:like", max = 10, windowSeconds = 60, message = "文章点赞过于频繁 请稍后再试")
    public Result<Boolean> like(@PathVariable Integer id) {
        try {
            return Result.ok("点赞成功", articleService.like(id));
        } catch (Exception e) {
            log.error("点赞失败", e);
            throw new BizException(ErrorCode.ARTICLE, "点赞失败");
        }
    }

    /** 取消点赞（1 分钟 10 次） */
    @PutMapping("/cancelLike/{id}")
    @RateLimit(key = "article:cancelLike", max = 10, windowSeconds = 60,
            message = "取消文章点赞过于频繁 请稍后再试")
    public Result<Boolean> cancelLike(@PathVariable Integer id) {
        try {
            return Result.ok("取消点赞成功", articleService.cancelLike(id));
        } catch (Exception e) {
            log.error("取消点赞失败", e);
            throw new BizException(ErrorCode.ARTICLE, "取消点赞失败");
        }
    }

    /** 增加阅读时长（毫秒，累加到 reading_duration） */
    @PutMapping("/addReadingDuration/{id}/{duration}")
    public Result<Boolean> addReadingDuration(@PathVariable Integer id,
                                              @PathVariable String duration) {
        try {
            return Result.ok("增加阅读时长成功",
                    articleService.addReadingDuration(id, Double.parseDouble(duration)));
        } catch (Exception e) {
            log.error("增加阅读时长失败", e);
            throw new BizException(ErrorCode.ARTICLE, "增加阅读时长失败");
        }
    }

    /**
     * 批量替换封面域名。原实现直接把 service 当 handler 用、没有设置 ctx.body，
     * 现网访问是 404（空响应）；这里补成可用的接口，见 README「已知差异」。
     */
    @PostMapping("/updateUrl")
    public Result<Integer> updateUrl() {
        try {
            return Result.ok("批量替换url成功", articleService.replaceCoverDomain());
        } catch (Exception e) {
            log.error("批量替换url失败", e);
            throw new BizException(ErrorCode.ARTICLE, "批量替换url失败");
        }
    }

    // ==================== 校验 ====================

    /** 对应 middleware/article 的 verifyArticleParam */
    private void verifyForm(ArticleForm form) {
        if (form.getCategory() == null) {
            throw new BizException(ErrorCode.ARTICLE, "文章分类必传");
        }
        Integer authorId = Num.toInteger(form.getAuthorId());
        if (form.getArticleTitle() == null || form.getArticleTitle().isBlank()
                || authorId == null || authorId == 0
                || form.getCategory().getCategoryName() == null
                || form.getCategory().getCategoryName().isBlank()
                || form.getArticleContent() == null || form.getArticleContent().isBlank()) {
            throw new BizException(ErrorCode.ARTICLE, "文章参数校验错误");
        }
        if (form.getTagList() == null || form.getTagList().isEmpty()) {
            throw new BizException(ErrorCode.ARTICLE, "文章标签不能为空");
        }
    }

    /** 对应 verifyTopParam / verifyDelParam */
    private void verifyNumber(String id, String value) {
        if (id == null || !id.matches(NUMBER) || value == null || !value.matches(NUMBER)) {
            throw new BizException(ErrorCode.ARTICLE, "参数只能为数字");
        }
    }
}
