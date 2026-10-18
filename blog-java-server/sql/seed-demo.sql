-- 演示数据：用于本地联调，验证首页文章列表的 categoryName / tagNameList 组装
INSERT INTO blog_config (id, blog_name, personal_say, blog_notice, view_time, created_at, updated_at)
VALUES (1, 'Rem的博客', '测试签名', '测试公告', 0, NOW(), NOW());

INSERT INTO blog_category (id, category_name, created_at, updated_at) VALUES
  (1, '后端', NOW(), NOW()),
  (2, '前端', NOW(), NOW());

INSERT INTO blog_tag (id, tag_name, created_at, updated_at) VALUES
  (1, 'Java', NOW(), NOW()),
  (2, 'Vue', NOW(), NOW());

INSERT INTO blog_user (id, username, password, role, nick_name, avatar, qq, ip, created_at, updated_at)
VALUES (1, 'rem', 'x', 1, 'Rem', '', '', '四川', NOW(), NOW());

INSERT INTO blog_header (id, route_name, bg_url, created_at, updated_at)
VALUES (1, 'Home', 'http://example.com/a.png', NOW(), NOW());

INSERT INTO blog_article
  (id, article_title, author_id, category_id, article_content, article_cover, is_top, `order`,
   status, type, created_at, updated_at, view_times, article_description, thumbs_up_times, reading_duration)
VALUES
  (1, '第一篇', 1, 1, '正文', '', 1, 1, 1, 1, NOW(), NOW(), 10, '描述一', 5, 12.5),
  (2, '第二篇', 1, 2, '正文', '', 2, 1, 1, 1, NOW(), NOW(), 20, '描述二', 3, 8.0);

INSERT INTO blog_article_tag (article_id, tag_id, created_at, updated_at) VALUES
  (1, 1, NOW(), NOW()),
  (1, 2, NOW(), NOW()),
  (2, 2, NOW(), NOW());
