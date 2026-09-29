package com.mymall.product.controller;

import com.mymall.common.api.CommonResult;
import com.mymall.product.model.CmsPrefrenceArea;
import com.mymall.product.service.CmsPrefrenceAreaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * 商品专题管理Controller
 */

@Controller
@Tag(name = "CmsPrefrenceAreaController", description = "商品专题管理")
@RequestMapping("/prefrenceArea")
public class CmsPerfrenceAreaController {
    @Autowired
    private CmsPrefrenceAreaService prefrenceAreaService;

    @Operation(summary = "获取所有商品优选")
    @RequestMapping(value = "/listAll", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<List<CmsPrefrenceArea>> listAll() {
        List<CmsPrefrenceArea> prefrenceAreaList = prefrenceAreaService.listAll();
        return CommonResult.success(prefrenceAreaList);
    }

}
