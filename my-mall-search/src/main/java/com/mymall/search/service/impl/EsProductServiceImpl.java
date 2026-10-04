package com.mymall.search.service.impl;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.StrUtil;
import co.elastic.clients.elasticsearch._types.aggregations.*;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.mymall.search.dao.EsProductDao;
import com.mymall.search.domain.EsProduct;
import com.mymall.search.domain.EsProductRelatedInfo;
import com.mymall.search.repository.EsProductRepository;
import com.mymall.search.service.EsProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.elasticsearch.client.elc.*;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EsProductServiceImpl implements EsProductService {
    //日志记录
    private static final Logger LOGGER = LoggerFactory.getLogger(EsProductServiceImpl.class);
    @Autowired
    private EsProductDao productDao;
    //Spring Data Elasticsearch 提供的数据访问接口
    @Autowired
    private EsProductRepository productRepository;
    @Autowired
    private ElasticsearchTemplate elasticsearchTemplate;


    /**
     * 从数据库中导入所有数据到ES
     * 批量导入防止oom
     * @return
     */
    @Override
    public int importAll() {
        int pageSize = 500;
        int pageNum = 1;
        int total = 0;

        while (true) {
            int offset = (pageNum - 1) * pageSize;
            List<EsProduct> list = productDao.getEsProductList(null, offset, pageSize);

            if (CollectionUtils.isEmpty(list)) {
                break;
            }

            productRepository.saveAll(list);
            total += list.size();
            LOGGER.info("导入第{}批，本批{}条，累计{}条", pageNum, list.size(), total);

            if (list.size() < pageSize) {
                break;
            }
            pageNum++;
        }

        LOGGER.info("全部导入完成，共{}条", total);
        return total;
    }

    @Override
    public void delete(Long id) {
        productRepository.deleteById(id);
    }

    @Override
    public void delete(List<Long> ids) {
        if (!org.springframework.util.CollectionUtils.isEmpty(ids)) {
            List<EsProduct> esProductList = new ArrayList<>();
            for (Long id : ids) {
                EsProduct esProduct = new EsProduct();
                esProduct.setId(id);
                esProductList.add(esProduct);
            }
            productRepository.deleteAll(esProductList);
        }
    }

    @Override
    public EsProduct create(Long id) {
        EsProduct result = null;
        List<EsProduct> esProductList = productDao.getAllEsProductList(id);
        if (esProductList.size() > 0) {
            EsProduct esProduct = esProductList.get(0);
            result = productRepository.save(esProduct);
        }
        return result;
    }

    /**
     * 综合搜索：关键词匹配 + 品牌/分类过滤 + 排序分页。
     *
     * 修复的bug：
     * 1. 排序逻辑：原代码 _score 排序无条件追加，导致选了新品/销量/价格排序后还会叠加相关度排序，
     *    改为 if-else 互斥，默认才按 _score 相关度排序。
     * 2. sort 判空：原代码 sort==1 时 Integer 自动拆箱，前端未传 sort 会抛 NPE，
     *    改为 sort != null && sort == 1。
     * 3. 分页页码：Spring Data 的 PageRequest 页码从0开始，前端传1开始，
     *    改为 PageRequest.of(pageNum, pageSize)，否则第一页查不到数据。
     */

    @Override
    public Page<EsProduct> search(String keyword, Integer pageNum, Integer pageSize) {
        Pageable pageable = PageRequest.of(pageNum, pageSize);
        return productRepository.findByNameOrSubTitleOrKeywords(keyword, keyword, keyword, pageable);
    }

    @Override
    public Page<EsProduct> search(String keyword, Long brandId, Long productCategoryId,
                                  Integer pageNum, Integer pageSize, Integer sort) {
        // Spring Data 页码从0开始，前端传1开始，需要减1
        Pageable pageable = PageRequest.of(pageNum, pageSize);
        NativeQueryBuilder nativeQueryBuilder = new NativeQueryBuilder();

        // 分页
        nativeQueryBuilder.withPageable(pageable);

        // 品牌/分类过滤
        if (brandId != null || productCategoryId != null) {
            Query boolQuery = QueryBuilders.bool(builder -> {
                if (brandId != null) {
                    builder.must(QueryBuilders.term(b -> b.field("brandId").value(brandId)));
                }
                if (productCategoryId != null) {
                    builder.must(QueryBuilders.term(b -> b.field("productCategoryId").value(productCategoryId)));
                }
                return builder;
            });
            nativeQueryBuilder.withFilter(boolQuery);
        }

        // 关键词搜索
        if (StrUtil.isEmpty(keyword)) {
            // 无关键词，查全部
            nativeQueryBuilder.withQuery(QueryBuilders.matchAll(builder -> builder));
        } else {
            // 有关键词，用 functionScore 给不同字段不同权重
            List<FunctionScore> functionScoreList = new ArrayList<>();

            // 商品名匹配，权重最高
            functionScoreList.add(new FunctionScore.Builder()
                    .filter(QueryBuilders.match(builder -> builder.field("name").query(keyword)))
                    .weight(10.0)
                    .build());

            // 副标题匹配，权重中等
            functionScoreList.add(new FunctionScore.Builder()
                    .filter(QueryBuilders.match(builder -> builder.field("subTitle").query(keyword)))
                    .weight(5.0)
                    .build());

            // 关键词匹配，权重最低
            functionScoreList.add(new FunctionScore.Builder()
                    .filter(QueryBuilders.match(builder -> builder.field("keywords").query(keyword)))
                    .weight(2.0)
                    .build());

            FunctionScoreQuery functionScoreQuery = QueryBuilders.functionScore()
                    .functions(functionScoreList)
                    .scoreMode(FunctionScoreMode.Sum)
                    .minScore(2.0)
                    .build();

            nativeQueryBuilder.withQuery(builder -> builder.functionScore(functionScoreQuery));
        }

        // 排序（互斥，默认按相关度）
        if (sort != null && sort == 1) {
            // 按新品从新到旧
            nativeQueryBuilder.withSort(Sort.by(Sort.Order.desc("id")));
        } else if (sort != null && sort == 2) {
            // 按销量从高到低
            nativeQueryBuilder.withSort(Sort.by(Sort.Order.desc("sale")));
        } else if (sort != null && sort == 3) {
            // 按价格从低到高
            nativeQueryBuilder.withSort(Sort.by(Sort.Order.asc("price")));
        } else if (sort != null && sort == 4) {
            // 按价格从高到低
            nativeQueryBuilder.withSort(Sort.by(Sort.Order.desc("price")));
        } else {
            // 默认按相关度
            nativeQueryBuilder.withSort(Sort.by(Sort.Order.desc("_score")));
        }

        // 构建查询并执行
        NativeQuery nativeQuery = nativeQueryBuilder.build();
        LOGGER.info("DSL:{}", nativeQuery.getQuery().toString());

        SearchHits<EsProduct> searchHits = elasticsearchTemplate.search(nativeQuery, EsProduct.class);

        if (searchHits.getTotalHits() <= 0) {
            PageImpl<EsProduct> esProducts = new PageImpl<>(ListUtil.empty(), pageable, 0);
            return esProducts;
        }

        List<EsProduct> searchProductList = searchHits.stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());

        return new PageImpl<>(searchProductList, pageable, searchHits.getTotalHits());
    }

    @Override
    public Page<EsProduct> recommend(Long id, Integer pageNum, Integer pageSize) {
        Pageable pageable = PageRequest.of(pageNum, pageSize);
        List<EsProduct> esProductList = productDao.getAllEsProductList(id);
        if (esProductList.size() > 0) {
            EsProduct esProduct = esProductList.get(0);
            String keyword = esProduct.getName();
            Long brandId = esProduct.getBrandId();
            Long productCategoryId = esProduct.getProductCategoryId();
            //构建查询条件
            NativeQueryBuilder nativeQueryBuilder = new NativeQueryBuilder();
            //分页
            nativeQueryBuilder.withPageable(pageable);
            //用于过滤掉相同的商品
            nativeQueryBuilder.withFilter(QueryBuilders.bool(build -> build.mustNot(QueryBuilders.term(b->b.field("id").value(id)))));
            //根据商品标题、品牌、分类进行搜索
            List<FunctionScore> functionScoreList = new ArrayList<>();
            functionScoreList.add(new FunctionScore.Builder()
                    .filter(QueryBuilders.match(builder -> builder.field("name").query(keyword)))
                    .weight(8.0)
                    .build());
            functionScoreList.add(new FunctionScore.Builder()
                    .filter(QueryBuilders.match(builder -> builder.field("subTitle").query(keyword)))
                    .weight(2.0)
                    .build());
            functionScoreList.add(new FunctionScore.Builder()
                    .filter(QueryBuilders.match(builder -> builder.field("keywords").query(keyword)))
                    .weight(2.0)
                    .build());
            functionScoreList.add(new FunctionScore.Builder()
                    .filter(QueryBuilders.match(builder -> builder.field("brandId").query(brandId)))
                    .weight(5.0)
                    .build());
            functionScoreList.add(new FunctionScore.Builder()
                    .filter(QueryBuilders.match(builder -> builder.field("productCategoryId").query(productCategoryId)))
                    .weight(3.0)
                    .build());
            FunctionScoreQuery.Builder functionScoreQueryBuilder = QueryBuilders.functionScore()
                    .functions(functionScoreList)
                    .scoreMode(FunctionScoreMode.Sum)
                    .minScore(2.0);
            nativeQueryBuilder.withQuery(builder -> builder.functionScore(functionScoreQueryBuilder.build()));
            NativeQuery nativeQuery = nativeQueryBuilder.build();
            LOGGER.info("DSL:{}", nativeQuery.getQuery().toString());
            SearchHits<EsProduct> searchHits = elasticsearchTemplate.search(nativeQuery, EsProduct.class);
            if(searchHits.getTotalHits()<=0){
                return new PageImpl<>(ListUtil.empty(),pageable,0);
            }
            List<EsProduct> searchProductList = searchHits.stream().map(SearchHit::getContent).collect(Collectors.toList());
            return new PageImpl<>(searchProductList,pageable,searchHits.getTotalHits());
        }
        return new PageImpl<>(ListUtil.empty(), pageable, 0);
    }

    @Override
    public EsProductRelatedInfo searchRelatedInfo(String keyword) {
        NativeQueryBuilder nativeQueryBuilder = new NativeQueryBuilder();
        //搜索条件
        if(StrUtil.isEmpty(keyword)){
            nativeQueryBuilder.withQuery(QueryBuilders.matchAll(builder -> builder));
        }else{
            nativeQueryBuilder.withQuery(QueryBuilders.multiMatch(builder -> builder.fields("name","subTitle","keywords").query(keyword)));
        }
        //聚合搜索品牌名称
        nativeQueryBuilder.withAggregation("brandNames", AggregationBuilders.terms(builder -> builder.field("brandName").size(10)));
        //聚合搜索分类名称
        nativeQueryBuilder.withAggregation("productCategoryNames",AggregationBuilders.terms(builder -> builder.field("productCategoryName").size(10)));
        //聚合搜索商品属性，去除type=0的属性
        Aggregation aggregation = new Aggregation.Builder().nested(builder -> builder.path("attrValueList"))
                .aggregations("productAttrs",new Aggregation.Builder()
                        .filter(b->b.term(a->a.field("attrValueList.type").value("1")))
                        .aggregations("attrIds",new Aggregation.Builder().terms(b->b.field("attrValueList.productAttributeId").size(10))
                                .aggregations("attrValues",new Aggregation.Builder().terms(b->b.field("attrValueList.value").size(10)).build())
                                .aggregations("attrNames",new Aggregation.Builder().terms(b->b.field("attrValueList.name").size(10)).build())
                                .build()).build()).build();
        nativeQueryBuilder.withAggregation("allAttrValues",aggregation);
        NativeQuery nativeQuery = nativeQueryBuilder.build();
        LOGGER.info("DSL:{}", nativeQueryBuilder.getQuery().toString());
        SearchHits<EsProduct> searchHits = elasticsearchTemplate.search(nativeQuery, EsProduct.class);
        return convertProductRelatedInfo(searchHits);
    }

    /**
     * 将返回结果转换为对象
     */
    private EsProductRelatedInfo convertProductRelatedInfo(SearchHits<EsProduct> response) {
        EsProductRelatedInfo productRelatedInfo = new EsProductRelatedInfo();
        Map<String, ElasticsearchAggregation> esAggregationMap = ((ElasticsearchAggregations) response.getAggregations()).aggregationsAsMap();
        //设置品牌
        ElasticsearchAggregation brandNames = esAggregationMap.get("brandNames");
        List<String> brandNameList = new ArrayList<>();
        List<StringTermsBucket> brandNameBuckets = ((StringTermsAggregate) brandNames.aggregation().getAggregate()._get()).buckets().array();
        for(int i = 0; i<brandNameBuckets.size(); i++){
            brandNameList.add(brandNameBuckets.get(i).key().stringValue());
        }
        productRelatedInfo.setBrandNames(brandNameList);
        //设置分类
        ElasticsearchAggregation productCategoryNames = esAggregationMap.get("productCategoryNames");
        List<String> productCategoryNameList = new ArrayList<>();
        List<StringTermsBucket> productCategoryNameBuckets = ((StringTermsAggregate) productCategoryNames.aggregation().getAggregate()._get()).buckets().array();
        for(int i = 0; i<productCategoryNameBuckets.size(); i++){
            productCategoryNameList.add(productCategoryNameBuckets.get(i).key().stringValue());
        }
        productRelatedInfo.setProductCategoryNames(productCategoryNameList);
        //设置参数
        ElasticsearchAggregation productAttrs = esAggregationMap.get("allAttrValues");
        List<LongTermsBucket> attrIdBuckets = ((LongTermsAggregate) ((FilterAggregate) ((NestedAggregate) productAttrs.aggregation().getAggregate()._get()).aggregations().get("productAttrs")._get()).aggregations().get("attrIds")._get()).buckets().array();
        List<EsProductRelatedInfo.ProductAttr> attrList = new ArrayList<>();
        for (LongTermsBucket item : attrIdBuckets) {
            EsProductRelatedInfo.ProductAttr attr = new EsProductRelatedInfo.ProductAttr();
            attr.setAttrId(item.key());
            List<String> attrValueList = new ArrayList<>();
            List<StringTermsBucket> attrValues = ((StringTermsAggregate) item.aggregations().get("attrValues")._get()).buckets().array();
            List<StringTermsBucket> attrNames = ((StringTermsAggregate) item.aggregations().get("attrNames")._get()).buckets().array();
            for (StringTermsBucket attrValue : attrValues) {
                attrValueList.add(attrValue.key().stringValue());
            }
            attr.setAttrValues(attrValueList);
            if(!org.springframework.util.CollectionUtils.isEmpty(attrNames)){
                String attrName = attrNames.get(0).key().stringValue();
                attr.setAttrName(attrName);
            }
            attrList.add(attr);
        }
        productRelatedInfo.setProductAttrs(attrList);
        return productRelatedInfo;
    }

//    @Override
//    public void delete(List<Long> ids) {
//        if (!CollectionUtils.isEmpty(ids)) {
//            productRepository.deleteAllById(ids);
//        }

}
