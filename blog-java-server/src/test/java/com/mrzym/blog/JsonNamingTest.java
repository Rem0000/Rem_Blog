package com.mrzym.blog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrzym.blog.dto.CategoryQuery;
import com.mrzym.blog.dto.DeleteRequest;
import com.mrzym.blog.entity.CategoryEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验命名策略：Java 驼峰 -> JSON 下划线，但 createdAt/updatedAt/pageSize
 * 以及 DeleteRequest 的两个列表字段保持驼峰（前端在用）。
 */
@JsonTest
class JsonNamingTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void entityFieldsAreSnakeCaseExceptTimestamps() throws Exception {
        CategoryEntity entity = new CategoryEntity();
        entity.setId(1);
        entity.setCategoryName("test");
        entity.setCreatedAt(LocalDateTime.of(2024, 1, 2, 3, 4, 5));

        String json = objectMapper.writeValueAsString(entity);

        assertThat(json).contains("\"category_name\":\"test\"");
        assertThat(json).doesNotContain("categoryName");
        assertThat(json).contains("\"createdAt\":\"2024-01-02 03:04:05\"");
        assertThat(json).doesNotContain("created_at");
    }

    @Test
    void queryBodyUsesPageAndPageSize() throws Exception {
        CategoryQuery query = objectMapper.readValue(
                "{\"page\":1,\"pageSize\":10,\"category_name\":\"abc\"}", CategoryQuery.class);

        assertThat(query.getPage()).isEqualTo(1);
        assertThat(query.getPageSize()).isEqualTo(10);
        assertThat(query.getCategoryName()).isEqualTo("abc");
    }

    @Test
    void deleteRequestKeepsCamelCaseListNames() throws Exception {
        DeleteRequest request = objectMapper.readValue(
                "{\"categoryIdList\":[1,2],\"tagIdList\":[3]}", DeleteRequest.class);

        assertThat(request.getCategoryIdList()).containsExactly(1, 2);
        assertThat(request.getTagIdList()).containsExactly(3);

        String json = objectMapper.writeValueAsString(request);
        assertThat(json).contains("categoryIdList");
        assertThat(json).doesNotContain("category_id_list");
    }

    @Test
    void pageResultUsesCamelCasePageSize() throws Exception {
        var page = com.mrzym.blog.common.PageResult.of(1, 10, 3, List.of());
        String json = objectMapper.writeValueAsString(page);

        assertThat(json).contains("\"page\":1");
        assertThat(json).contains("\"pageSize\":10");
        assertThat(json).doesNotContain("page_size");
    }
}
