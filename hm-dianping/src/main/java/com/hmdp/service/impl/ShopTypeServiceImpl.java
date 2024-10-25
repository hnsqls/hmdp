package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author ls
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

   static  final String shop_type_key = RedisConstants.SHOP_TYPE_KEY;
    /**
     * 查询标签，并添加缓存
     * @return
     */
    @Override
    public List<ShopType> listWithCache() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        //查询缓存
        List<String> typelist = stringRedisTemplate.opsForList().range(shop_type_key, 0, -1);
        if (typelist != null && !typelist.isEmpty()) { //缓存不为空
            // 将List<String> -> List<ShopType>
            List<ShopType> resultList = new ArrayList<>();
            for (String string : typelist) {
                ShopType shopType = objectMapper.readValue(string, ShopType.class);
                    resultList.add(shopType);
            }


            return  resultList;
        }

        //缓存为空，查询数据库
        List<ShopType> resultList =query().orderByAsc("sort").select("id","name","sort","icon").list();
        for (ShopType shopType : resultList) {
            //将查询结果存入缓存
            stringRedisTemplate.opsForList().rightPush(shop_type_key, JSONUtil.toJsonStr(shopType));
            stringRedisTemplate.expire(shop_type_key,RedisConstants.SHOP_TYPE_TTL, TimeUnit.HOURS);
        }

        return resultList;
    }
}
