package com.mymall.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 统一分页结构。
 * 字段与原项目一致：pageNum / pageSize / totalPage / total / list
 *
 * ★ list 上的 @JsonInclude(NON_EMPTY) 是"逐字节对齐老服务"的关键：
 *   老服务 8085 在空结果时返回 {"pageNum":1,"pageSize":5,"totalPage":0,"total":0}（**不含 list**），
 *   而本类 of() 为了防御会把 null 兜成 Collections.emptyList()；
 *   若不加此注解，NON_NULL 不拦空集合 → 会多出 "list":[] → 与 8085 逐字节不一致（D10a 实测发现）。
 *   加上后：空集合被省略（与老服务一致），非空集合照常输出。
 */
public class CommonPage<T> implements Serializable {

    private Integer pageNum;
    private Integer pageSize;
    private Integer totalPage;
    private Long total;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<T> list;

    public static <T> CommonPage<T> of(Integer pageNum, Integer pageSize, Long total, List<T> list) {
        CommonPage<T> page = new CommonPage<>();
        page.setPageNum(pageNum);
        page.setPageSize(pageSize);
        page.setTotal(total);
        page.setList(list == null ? Collections.emptyList() : list);
        if (pageSize == null || pageSize <= 0) {
            page.setTotalPage(0);
        } else {
            page.setTotalPage((int) Math.ceil((double) (total == null ? 0 : total) / pageSize));
        }
        return page;
    }

    public Integer getPageNum() { return pageNum; }
    public void setPageNum(Integer pageNum) { this.pageNum = pageNum; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
    public Integer getTotalPage() { return totalPage; }
    public void setTotalPage(Integer totalPage) { this.totalPage = totalPage; }
    public Long getTotal() { return total; }
    public void setTotal(Long total) { this.total = total; }
    public List<T> getList() { return list; }
    public void setList(List<T> list) { this.list = list; }
}