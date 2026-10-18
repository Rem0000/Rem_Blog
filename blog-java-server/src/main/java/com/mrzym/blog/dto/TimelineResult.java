package com.mrzym.blog.dto;

import lombok.Data;

import java.util.List;

/**
 * 时间轴分页结果。current/size 来自路径参数，Node 里原样是字符串，
 * 所以这里用 String 回显（现网实测 "1"/"4"）。
 */
@Data
public class TimelineResult {

    private String current;

    private String size;

    private List<TimelineGroup> list;

    private long total;

    public static TimelineResult of(String current, String size, long total, List<TimelineGroup> list) {
        TimelineResult r = new TimelineResult();
        r.current = current;
        r.size = size;
        r.list = list;
        r.total = total;
        return r;
    }
}
