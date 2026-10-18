package com.mrzym.blog.controller;

import com.mrzym.blog.auth.RequireRole;
import com.mrzym.blog.common.BizException;
import com.mrzym.blog.common.ErrorCode;
import com.mrzym.blog.common.PageResult;
import com.mrzym.blog.common.Result;
import com.mrzym.blog.dto.DeleteRequest;
import com.mrzym.blog.dto.TagForm;
import com.mrzym.blog.dto.TagQuery;
import com.mrzym.blog.entity.TagEntity;
import com.mrzym.blog.service.TagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@RestController
@RequestMapping("/tag")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @PostMapping("/add")
    @RequireRole(RequireRole.Level.ADMIN)
    public Result<Map<String, Object>> add(@RequestBody TagForm form) {
        if (form.getTagName() == null || form.getTagName().isBlank()) {
            throw new BizException(ErrorCode.TAG, "标签名称不能为空");
        }
        TagEntity exist = tagService.getOneTag(null, form.getTagName());
        // tag 这边原实现用的是 !==，即"同名但 id 不同"才算重复，逻辑是对的
        if (exist != null && !Objects.equals(exist.getId(), form.getId())) {
            throw new BizException(ErrorCode.TAG, "标签已存在");
        }
        try {
            TagEntity created = tagService.createTag(form.getTagName());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", created.getId());
            data.put("tag_name", created.getTagName());
            return Result.ok("新增标签成功", data);
        } catch (Exception e) {
            log.error("新增标签失败", e);
            throw new BizException(ErrorCode.TAG, "新增标签失败");
        }
    }

    @PutMapping("/update")
    @RequireRole(RequireRole.Level.ADMIN)
    public Result<Boolean> update(@RequestBody TagForm form) {
        if (form.getTagName() == null || form.getTagName().isBlank()) {
            throw new BizException(ErrorCode.TAG, "标签名称不能为空");
        }
        TagEntity exist = tagService.getOneTag(null, form.getTagName());
        if (exist != null && !Objects.equals(exist.getId(), form.getId())) {
            throw new BizException(ErrorCode.TAG, "标签已存在");
        }
        try {
            boolean ok = tagService.updateTag(form.getId(), form.getTagName());
            return Result.ok("修改标签成功", ok);
        } catch (Exception e) {
            log.error("修改标签失败", e);
            throw new BizException(ErrorCode.TAG, "修改标签失败");
        }
    }

    @PostMapping("/delete")
    @RequireRole(RequireRole.Level.ADMIN)
    public Result<Map<String, Object>> delete(@RequestBody DeleteRequest request) {
        List<Integer> ids = request.getTagIdList();
        if (ids == null || ids.isEmpty()) {
            throw new BizException(ErrorCode.TAG, "标签id列表不能为空");
        }
        try {
            int affected = tagService.deleteTags(ids);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("updateNum", affected);
            return Result.ok("删除标签成功", data);
        } catch (Exception e) {
            log.error("删除标签失败", e);
            throw new BizException(ErrorCode.TAG, "删除标签失败");
        }
    }

    @PostMapping("/getTagList")
    public Result<PageResult<TagEntity>> list(@RequestBody TagQuery query) {
        try {
            PageResult<TagEntity> page = tagService.getTagList(
                    query.getPage(), query.getPageSize(), query.getTagName());
            return Result.ok("分页查找标签成功", page);
        } catch (Exception e) {
            log.error("分页查找标签失败", e);
            throw new BizException(ErrorCode.TAG, "分页查找标签失败");
        }
    }

    @GetMapping("/getTagDictionary")
    public Result<List<Map<String, Object>>> dictionary() {
        try {
            return Result.ok("获取标签字典成功", tagService.getTagDictionary());
        } catch (Exception e) {
            log.error("获取标签字典失败", e);
            throw new BizException(ErrorCode.TAG, "获取标签字典失败");
        }
    }
}
