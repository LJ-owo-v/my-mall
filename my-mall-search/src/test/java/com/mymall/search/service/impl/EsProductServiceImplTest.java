package com.mymall.search.service.impl;

import com.mymall.search.dao.EsProductDao;
import com.mymall.search.domain.EsProduct;
import com.mymall.search.repository.EsProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EsProductServiceImpl 纯单元测试。
 *
 * <p>ES 本体不启动，改为对 {@link ElasticsearchTemplate} 打桩，然后<b>检查生成的查询 DSL</b>：
 * 排序字段/方向、空关键词是否走 matchAll、返回 0 条时是否安全降级为空 Page。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EsProductServiceImpl")
class EsProductServiceImplTest {

    @Mock private EsProductDao productDao;
    @Mock private EsProductRepository productRepository;
    @Mock private ElasticsearchTemplate elasticsearchTemplate;

    @InjectMocks private EsProductServiceImpl esProductService;

    @Test
    @DisplayName("search_sort为null_不抛NPE且默认按_score降序")
    void search_sort为null_不抛NPE且默认按_score降序() {
        stubEmptyHits();

        // 修复过的拆箱 bug：sort 为 null 时 Integer 自动拆箱会 NPE
        Page<EsProduct> page = esProductService.search("手机", null, null, 1, 10, null);

        assertThat(page).isNotNull();
        Sort.Order order = firstSort(capturedQuery(1));
        assertThat(order.getProperty()).isEqualTo("_score");
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("search_sort为1_按id降序")
    void search_sort为1_按id降序() {
        stubEmptyHits();

        esProductService.search("手机", null, null, 1, 10, 1);

        Sort.Order order = firstSort(capturedQuery(1));
        assertThat(order.getProperty()).isEqualTo("id");
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("search_sort为234_分别按sale降序_price升序_price降序")
    void search_sort为234_分别按sale降序_price升序_price降序() {
        stubEmptyHits();

        esProductService.search("手机", null, null, 1, 10, 2);
        esProductService.search("手机", null, null, 1, 10, 3);
        esProductService.search("手机", null, null, 1, 10, 4);

        ArgumentCaptor<NativeQuery> captor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(elasticsearchTemplate, times(3)).search(captor.capture(), eq(EsProduct.class));
        List<NativeQuery> queries = captor.getAllValues();

        Sort.Order newest = firstSort(queries.get(0));
        assertThat(newest.getProperty()).isEqualTo("sale");
        assertThat(newest.getDirection()).isEqualTo(Sort.Direction.DESC);

        Sort.Order priceAsc = firstSort(queries.get(1));
        assertThat(priceAsc.getProperty()).isEqualTo("price");
        assertThat(priceAsc.getDirection()).isEqualTo(Sort.Direction.ASC);

        Sort.Order priceDesc = firstSort(queries.get(2));
        assertThat(priceDesc.getProperty()).isEqualTo("price");
        assertThat(priceDesc.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("search_keyword为空_走matchAll")
    void search_keyword为空_走matchAll() {
        stubEmptyHits();

        esProductService.search("", null, null, 1, 10, null);

        assertThat(capturedQuery(1).getQuery().isMatchAll()).isTrue();
    }

    @Test
    @DisplayName("search_有关键词_用functionScore并按字段加权")
    void search_有关键词_用functionScore并按字段加权() {
        stubEmptyHits();

        esProductService.search("手机", 1L, 2L, 1, 10, null);

        NativeQuery query = capturedQuery(1);
        assertThat(query.getQuery().isFunctionScore()).isTrue();
        // 品牌/分类过滤走 filter 而不是 query，否则会参与打分、把结果相关性算歪
        assertThat(query.getFilter()).isNotNull();
        assertThat(query.getFilter().isBool()).isTrue();
        // 关键词查不到时要能拿到 minScore 门槛
        assertThat(query.getQuery().functionScore().minScore()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("search_查询无结果_返回空Page且total为0")
    void search_查询无结果_返回空Page且total为0() {
        stubEmptyHits();

        Page<EsProduct> page = esProductService.search("不存在的商品", null, null, 1, 10, null);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("importAll_dao分批返回_循环能正常退出")
    void importAll_dao分批返回_循环能正常退出() {
        // 第一批满 500 条 → 继续翻页；第二批 0 条 → 退出
        List<EsProduct> firstBatch = Collections.nCopies(500, new EsProduct());
        when(productDao.getEsProductList(isNull(), eq(0), eq(500))).thenReturn(firstBatch);
        when(productDao.getEsProductList(isNull(), eq(500), eq(500))).thenReturn(List.of());

        int total = esProductService.importAll();

        assertThat(total).isEqualTo(500);
        // 只在有数据的那一批写 ES，空批不能白调一次 saveAll
        verify(productRepository, times(1)).saveAll(firstBatch);
    }


    @Test
    @DisplayName("recommend_商品不存在_返回空Page不抛异常")
    void recommend_商品不存在_返回空Page不抛异常() {
        when(productDao.getAllEsProductList(404L)).thenReturn(List.of());

        Page<EsProduct> page = esProductService.recommend(404L, 1, 10);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("recommend_命中商品_排除自身并返回分页结果")
    void recommend_命中商品_排除自身并返回分页结果() {
        EsProduct seed = new EsProduct();
        seed.setId(1L);
        seed.setName("手机");
        seed.setBrandId(11L);
        seed.setProductCategoryId(22L);
        when(productDao.getAllEsProductList(1L)).thenReturn(List.of(seed));

        SearchHits<EsProduct> hits = mock(SearchHits.class);
        when(hits.getTotalHits()).thenReturn(1L);
        SearchHit<EsProduct> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(seed);
        when(hits.stream()).thenReturn(Stream.of(hit));
        when(elasticsearchTemplate.search(any(NativeQuery.class), eq(EsProduct.class))).thenReturn(hits);

        Page<EsProduct> page = esProductService.recommend(1L, 1, 10);

        assertThat(page.getContent()).hasSize(1);
        // 记录一个可疑点：PageRequest.of(1, 10) 的 offset 是 10，说明代码把"前端页码"直接当成了
        // Spring Data 的 0 基索引 —— 前端请求第 1 页会跳过前 10 条（详见回报"疑似缺陷"第 4 条）
        assertThat(page.getPageable().getOffset()).isEqualTo(10);
        NativeQuery query = capturedQuery(1);
        // 推荐必须把当前商品本身排除掉，否则会向用户推荐"他正在看的商品"
        assertThat(query.getFilter()).isNotNull();
        assertThat(query.getQuery().isFunctionScore()).isTrue();
    }

    // ==================== 夹具 ====================

    @SuppressWarnings("unchecked")
    private void stubEmptyHits() {
        SearchHits<EsProduct> hits = mock(SearchHits.class);
        when(hits.getTotalHits()).thenReturn(0L);
        when(elasticsearchTemplate.search(any(NativeQuery.class), eq(EsProduct.class))).thenReturn(hits);
    }

    /** NativeQuery 的排序通过 Spring Data 的 Sort 承载（不是 ES 原生 SortOptions） */
    private Sort.Order firstSort(NativeQuery query) {
        return query.getSort().iterator().next();
    }

    private NativeQuery capturedQuery(int times) {
        ArgumentCaptor<NativeQuery> captor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(elasticsearchTemplate, times(times)).search(captor.capture(), eq(EsProduct.class));
        return captor.getAllValues().get(times - 1);
    }
}
