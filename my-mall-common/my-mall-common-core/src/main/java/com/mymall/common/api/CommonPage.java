package com.mymall.common.api;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 统一分页结构。
 * 字段与原项目一致：pageNum / pageSize / totalPage / total / list
 */
public class CommonPage<T> implements Serializable {

    private Integer pageNum;
    private Integer pageSize;
    private Integer totalPage;
    private Long total;
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