package com.mrzym.blog.dto;

import lombok.Data;

/**
 * 友链新增/修改请求体。
 *
 * 注意：id / status / user_id 在前端可能是数字、字符串或空串
 * （后台表单初始值就是 id: ""），所以用 Object 承接，避免反序列化失败。
 */
@Data
public class LinkForm {

    private Object id;

    /** JSON: site_name */
    private String siteName;

    /** JSON: site_desc */
    private String siteDesc;

    /** JSON: site_avatar */
    private String siteAvatar;

    private String url;

    private Object status;

    /** JSON: user_id */
    private Object userId;
}
