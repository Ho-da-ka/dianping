
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
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    private ShopServiceImpl shopService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void loadShopData() {
        //1.查询店铺信息
        List<Shop> list = shopService.list();
        //2.把店铺分组，按照typeId分组，typeId一致的放到一个集合
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //3.分批完成写入Redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            //3.1.获取类型id
            Long typeid = entry.getKey();
            String key = "shop:geo:" + typeid;
            //3.2.获取同类型的店铺的集合
            List<Shop> value = entry.getValue();
            //3.3.写入redis GEOADD key 经度 纬度 member
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            for (Shop shop : value) {
//                stringRedisTemplate.opsForGeo().add(key,new Point(shop.getX(),shop.getY()),shop.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }

    }

    @Test
    void testHyperLogLog() {
        String[] values = new String[1000];
        int j = 0;
        for (int i = 0; i < 1000000; i++) {
            j = j % 1000;
            values[j] = "user_" + i;
            if (j == 999) {
                stringRedisTemplate.opsForHyperLogLog().add("hl3 ", values);
            }
            j++;
        }
        //统计数量
        Long res = stringRedisTemplate.opsForHyperLogLog().size("hl3");
        System.out.println("hl3" + res);
    }
}
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


