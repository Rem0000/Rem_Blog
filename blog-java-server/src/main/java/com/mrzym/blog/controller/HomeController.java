package com.mrzym.blog.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 根路径，对齐原实现 router/index.js 的欢迎语（Koa 默认 text/plain）。
 */
@RestController
public class HomeController {

    @GetMapping(value = "/", produces = "text/plain;charset=UTF-8")
    public String home() {
        return "欢迎 这是后台server首页";
    }
}
