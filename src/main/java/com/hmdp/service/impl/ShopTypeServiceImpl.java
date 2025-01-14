package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        Set<String> members = stringRedisTemplate.opsForSet().members("cache:shop:type");
        if (members != null && !members.isEmpty()) {
            List<ShopType> typeList = new ArrayList<>();
            members.forEach(type -> typeList.add(JSONUtil.toBean(type, ShopType.class)));
            return Result.ok(typeList);
        }
        List<ShopType> typeList = query().orderByAsc("sort").list();
        typeList.forEach(type -> stringRedisTemplate.opsForSet().add("cache:shop:type", JSONUtil.toJsonStr(type)));
        return Result.ok(typeList);
    }
}
