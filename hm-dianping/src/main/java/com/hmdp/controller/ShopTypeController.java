package com.hmdp.controller;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
ls
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;

    /**
     * 查询标签列表
     * @return
     */
    @GetMapping("list")
    public Result queryTypeList() throws JsonProcessingException {
//        List<ShopType> typeList = typeService
//                .query().orderByAsc("sort").list();
       List<ShopType> typeList =  typeService.listWithCache();
        return Result.ok(typeList);
    }
}
