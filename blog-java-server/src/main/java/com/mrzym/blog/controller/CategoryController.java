package com.mrzym.blog.controller;

import com.mrzym.blog.auth.RequireRole;
import com.mrzym.blog.common.BizException;
import com.mrzym.blog.common.ErrorCode;
import com.mrzym.blog.common.PageResult;
import com.mrzym.blog.common.Result;
import com.mrzym.blog.dto.CategoryForm;
import com.mrzym.blog.dto.CategoryQuery;
import com.mrzym.blog.dto.DeleteRequest;
import com.mrzym.blog.entity.CategoryEntity;
import com.mrzym.blog.service.CategoryService;
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
@RequestMapping("/category")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping("/add")
    @RequireRole(RequireRole.Level.ADMIN_NOT_SUPER)
    public Result<Map<String, Object>> add(@RequestBody CategoryForm form) {
        // 对应 middleware/category 的 verifyCategory
        if (form.getCategoryName() == null || form.getCategoryName().isBlank()) {
            throw new BizException(ErrorCode.CATEGORY, "分类名称不能为空");
        }
        CategoryEntity exist = categoryService.getOneCategory(null, form.getCategoryName());
        // 注意：原实现这里是 res.id == id（不是 !==），属于既有行为，先照抄；
        // 真要修正的话把下面的 equals 改成 !equals，与 tag 保持一致。
        if (exist != null && Objects.equals(exist.getId(), form.getId())) {
            throw new BizException(ErrorCode.CATEGORY, "分类已存在");
        }
        try {
            CategoryEntity created = categoryService.createCategory(form.getCategoryName());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", created.getId());
            data.put("category_name", created.getCategoryName());
            return Result.ok("新增分类成功", data);
        } catch (Exception e) {
            log.error("新增分类失败", e);
            throw new BizException(ErrorCode.CATEGORY, "新增分类失败");
        }
    }

    @PutMapping("/update")
    @RequireRole(RequireRole.Level.ADMIN_NOT_SUPER)
    public Result<Boolean> update(@RequestBody CategoryForm form) {
        if (form.getCategoryName() == null || form.getCategoryName().isBlank()) {
            throw new BizException(ErrorCode.CATEGORY, "分类名称不能为空");
        }
        CategoryEntity exist = categoryService.getOneCategory(null, form.getCategoryName());
        if (exist != null && Objects.equals(exist.getId(), form.getId())) {
            throw new BizException(ErrorCode.CATEGORY, "分类已存在");
        }
        try {
            boolean ok = categoryService.updateCategory(form.getId(), form.getCategoryName());
            return Result.ok("修改分类成功", ok);
        } catch (Exception e) {
            log.error("修改分类失败", e);
            throw new BizException(ErrorCode.CATEGORY, "修改分类失败");
        }
    }

    @PostMapping("/delete")
    @RequireRole(RequireRole.Level.ADMIN_NOT_SUPER)
    public Result<Map<String, Object>> delete(@RequestBody DeleteRequest request) {
        List<Integer> ids = request.getCategoryIdList();
        if (ids == null || ids.isEmpty()) {
            throw new BizException(ErrorCode.CATEGORY, "分类id列表不能为空");
        }
        try {
            int affected = categoryService.deleteCategories(ids);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("updateNum", affected);
            return Result.ok("删除分类成功", data);
        } catch (Exception e) {
            log.error("删除分类失败", e);
            throw new BizException(ErrorCode.CATEGORY, "删除分类失败");
        }
    }

    @PostMapping("/getCategoryList")
    public Result<PageResult<CategoryEntity>> list(@RequestBody CategoryQuery query) {
        try {
            PageResult<CategoryEntity> page = categoryService.getCategoryList(
                    query.getPage(), query.getPageSize(), query.getCategoryName());
            return Result.ok("分页查找分类成功", page);
        } catch (Exception e) {
            log.error("分页查找分类失败", e);
            throw new BizException(ErrorCode.CATEGORY, "分页查找分类失败");
        }
    }

    @GetMapping("/getCategoryDictionary")
    public Result<List<Map<String, Object>>> dictionary() {
        try {
            return Result.ok("获取分类字典成功", categoryService.getCategoryDictionary());
        } catch (Exception e) {
            log.error("获取分类字典失败", e);
            throw new BizException(ErrorCode.CATEGORY, "获取分类字典失败");
        }
    }
}
