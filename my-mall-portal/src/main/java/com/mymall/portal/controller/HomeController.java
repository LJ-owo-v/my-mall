package com.mymall.portal.controller;

import com.mymall.common.api.CommonResult;
import com.mymall.portal.domain.HomeContentResult;
import com.mymall.portal.model.CmsSubject;
import com.mymall.portal.model.PmsProduct;
import com.mymall.portal.model.PmsProductCategory;
import com.mymall.portal.service.HomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 首页内容管理Controller
 *
 * 不管用哪种分页方式，**6 个接口里 4 个分页接口都返回 `List` 而不是 `CommonPage`**，原因和 hotProductList 一样：
 * - 首页是无限滚动，不需要 total/totalPage
 * - 前端靠返回空数组判断是否加载完毕
 * - 少一次 COUNT 查询（offset 方式）或不提取 COUNT（PageHelper 方式）
 */

@Controller
@Tag(name = "HomeController", description = "首页内容管理")
@RequestMapping("/home")
public class HomeController {
    @Autowired
    private HomeService homeService;

    @Operation(summary = "首页内容页信息展示")
    @RequestMapping(value = "/content", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<HomeContentResult> content() {
        HomeContentResult contentResult = homeService.content();
        return CommonResult.success(contentResult);
    }

    @Operation(summary = "分页获取推荐商品")
    @RequestMapping(value = "/recommendProductList", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<List<PmsProduct>> recommendProductList(@RequestParam(value = "pageSize", defaultValue = "4") Integer pageSize,
                                                               @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum) {
        List<PmsProduct> productList = homeService.recommendProductList(pageSize, pageNum);
        return CommonResult.success(productList);
    }
    @Operation(summary = "获取首页商品分类")
    @RequestMapping(value = "/productCateList/{parentId}", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<List<PmsProductCategory>> getProductCateList(@PathVariable Long parentId) {
        List<PmsProductCategory> productCategoryList = homeService.getProductCateList(parentId);
        return CommonResult.success(productCategoryList);
    }
    @Operation(summary = "根据分类获取专题")
    @RequestMapping(value = "/subjectList", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<List<CmsSubject>> getSubjectList(@RequestParam(required = false) Long cateId,
                                                         @RequestParam(value = "pageSize", defaultValue = "4") Integer pageSize,
                                                         @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum) {
        List<CmsSubject> subjectList = homeService.getSubjectList(cateId,pageSize,pageNum);
        return CommonResult.success(subjectList);
    }
    /**
     * 分页获取人气推荐商品
     *
     * ================================================================
     * 【一、为什么需要分页？】
     * 人气推荐商品可能有几百上千个，一次性全部查出来：
     *   - 数据库查询慢、内存占用大
     *   - 网络传输数据量大，首页加载慢
     *   - 用户一次只能看到 6 个，查 1000 个是浪费
     * 所以每次只查当前页的 6 条，用户下滑再查下一页。
     *
     * ================================================================
     * 【二、分页原理：offset 手动分页】
     * 不使用 PageHelper，而是手动计算 offset 传给 SQL 的 LIMIT。
     *
     * offset 计算公式：offset = pageSize × (pageNum - 1)
     *   pageNum=1 → offset=0  → LIMIT 0,6   （跳过0条，取6条 = 第1页）
     *   pageNum=2 → offset=6  → LIMIT 6,6   （跳过6条，取6条 = 第2页）
     *   pageNum=3 → offset=12 → LIMIT 12,6  （跳过12条，取6条 = 第3页）
     *   pageNum=4 → offset=18 → LIMIT 18,6  （如果只剩2条，就返回2条）
     *   pageNum=5 → offset=24 → LIMIT 24,6  （没有数据了，返回空数组 []）
     *
     * ================================================================
     * 【三、为什么返回 List 而不是 CommonPage（分页结构）？】
     * CommonPage 包含 pageNum/pageSize/total/totalPage/list，需要查 total（总条数）。
     * 查 total 需要额外执行 SELECT COUNT(*)，多一次数据库查询。
     *
     * 首页人气推荐是"无限滚动"模式：
     *   - 前端不显示"第1/4页"分页器，不需要知道总页数
     *   - 用户下滑 → 发请求 → 返回空数组就停
     * 所以不需要 total，少一次 COUNT 查询，首页响应更快。
     *
     * 对比：商品搜索接口 /product/search 返回 CommonPage，
     * 因为搜索结果页需要显示"共找到 100 件商品"，必须知道 total。
     *
     * ================================================================
     * 【四、前端如何判断是否还有更多？】
     *   返回 data 有数据 → 继续展示，用户下滑可以再请求下一页
     *   返回 data 为空数组 [] → 显示"没有更多了"，停止请求
     *
     * ================================================================
     * 【五、排序规则】
     * 按销量 sale 降序，销量越高的商品越靠前（人气推荐）。
     *
     * ================================================================
     * 【六、参数说明】
     * @param pageNum  页码，从1开始，默认1
     * @param pageSize 每页条数，默认6
     * @return 当前页的商品列表（不含分页信息，空数组表示没有更多了）
     */
    @Operation(summary = "分页获取人气推荐商品")
    @RequestMapping(value = "/hotProductList", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<List<PmsProduct>> hotProductList(@RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                                         @RequestParam(value = "pageSize", defaultValue = "6") Integer pageSize) {
        List<PmsProduct> productList = homeService.hotProductList(pageNum,pageSize);
        return CommonResult.success(productList);
    }
    @Operation(summary = "分页获取新品推荐商品")
    @RequestMapping(value = "/newProductList", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<List<PmsProduct>> newProductList(@RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                                         @RequestParam(value = "pageSize", defaultValue = "6") Integer pageSize) {
        List<PmsProduct> productList = homeService.newProductList(pageNum,pageSize);
        return CommonResult.success(productList);
    }

}
