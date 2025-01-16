
package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    private ShopServiceImpl shopService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
/*
    @Test
    public void test() {
        shopService.saveShopToRedis(1L, 60L);
        shopService.saveShopToRedis(2L, 60L);
        shopService.saveShopToRedis(3L, 60L);
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries("cache:shop:1");
        RedisData redisData = BeanUtil.fillBeanWithMap(entries, new RedisData(), false);
        String json = (String) redisData.getData();
        JSONObject jsonObject = JSONUtil.parseObj(json);
        Shop shop = BeanUtil.toBean(jsonObject, Shop.class);
//        BeanUtil.fillBean()
        System.out.println(shop);
    }
*/
}

