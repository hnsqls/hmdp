package com.hmdp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.hmdp.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * @author ls
 */
public interface IShopTypeService extends IService<ShopType> {

    List<ShopType> listWithCache() throws JsonProcessingException;
}
