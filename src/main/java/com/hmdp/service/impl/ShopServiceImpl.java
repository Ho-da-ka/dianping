package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        // 缓存穿透
        Shop shop = cacheClient
                .queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.SECONDS);

        // 逻辑过期解决缓存击穿
//        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

//    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /*// 逻辑过期解决缓存击穿
    private Shop queryWithLogical(Long id) {
        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(CACHE_SHOP_KEY + id);
        // 判断是否命中
        if (!map.isEmpty()) {
            RedisData redisData = BeanUtil.fillBeanWithMap(map, new RedisData(), false);
            if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
                if (tryLock(LOCK_SHOP_KEY + id)) {
                    if (redisData.getExpireTime().isBefore(LocalDateTime.now())) {
                        String json = (String) redisData.getData();
                        JSONObject jsonObject = JSONUtil.parseObj(json);
                        return BeanUtil.toBean(jsonObject, Shop.class);
                    }
                    CACHE_REBUILD_EXECUTOR.submit(() -> {
                        try {
                            saveShopToRedis(id, 20L);
                        } finally {
                            unLock(LOCK_SHOP_KEY + id);
                        }
                    });
                }
            }
            String json = (String) redisData.getData();
            JSONObject jsonObject = JSONUtil.parseObj(json);
            return BeanUtil.toBean(jsonObject, Shop.class);
        }
        return null;
    }*/

    /*// 互斥锁解决缓存击穿
    private Shop queryWithMutex(Long id) {
        Shop shop = new Shop();
        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(CACHE_SHOP_KEY + id);
        if (!map.isEmpty()) {
            if (map.containsKey("empty")) {
                log.info("拦截到无效请求");
                return null;
            }
            BeanUtil.fillBeanWithMap(map, shop, false);
            return shop;
        }
        if (tryLock(LOCK_SHOP_KEY + id)) {
            map = stringRedisTemplate.opsForHash().entries(CACHE_SHOP_KEY + id);
            if (!map.isEmpty()) {
                unLock(LOCK_SHOP_KEY + id);
                return BeanUtil.fillBeanWithMap(map, shop, false);
            }
            shop = getById(id);
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (shop == null) {
                stringRedisTemplate.opsForHash().put(CACHE_SHOP_KEY + id, "empty", "");
                stringRedisTemplate.expire(CACHE_SHOP_KEY + id, CACHE_NULL_TTL, TimeUnit.MINUTES);
                unLock(LOCK_SHOP_KEY + id);
                return null;
            }
            // 创建一个新 Map 来存储转换后的数据
            Map<String, String> stringMap = new HashMap<>();
            BeanUtil.beanToMap(shop).forEach((key, value) -> {
                // 确保所有值都转换为字符串
                stringMap.put(key, value != null ? value.toString() : null);
            });
            stringRedisTemplate.opsForHash().putAll(CACHE_SHOP_KEY + id, stringMap);
            stringRedisTemplate.expire(CACHE_SHOP_KEY + id, CACHE_SHOP_TTL, TimeUnit.MINUTES);
            unLock(LOCK_SHOP_KEY + id);
        } else {
            try {
                Thread.sleep(50);
                return queryWithMutex(id);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        return shop;
    }*/

    /*// 缓存穿透
    private Shop queryWithPassThrough(Long id) {
        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(CACHE_SHOP_KEY + id);
        if (!map.isEmpty()) {
            if (map.containsKey("empty")) {
                log.info("拦截到无效请求");
                return null;
            }
            Shop shop = new Shop();
            BeanUtil.fillBeanWithMap(map, shop, false);
            return shop;
        }
        Shop shop = getById(id);
        if (shop == null) {
            stringRedisTemplate.opsForHash().put(CACHE_SHOP_KEY + id, "empty", "");
            stringRedisTemplate.expire(CACHE_SHOP_KEY + id, CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 创建一个新 Map 来存储转换后的数据
        Map<String, String> stringMap = new HashMap<>();
        BeanUtil.beanToMap(shop).forEach((key, value) -> {
            // 确保所有值都转换为字符串
            stringMap.put(key, value != null ? value.toString() : null);
        });
        stringRedisTemplate.opsForHash().putAll(CACHE_SHOP_KEY + id, stringMap);
        stringRedisTemplate.expire(CACHE_SHOP_KEY + id, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(flag);
    }

    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }*/

    /*// 数据预热
    public void saveShopToRedis(Long id, Long expireSeconds) {
        Shop shop = getById(id);
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        Map<String, String> hash = new HashMap<>();
        BeanUtil.beanToMap(redisData).forEach((key, value) -> {
            if (key.equals("expireTime")) {
                hash.put(key, value != null ? value.toString() : null);
            } else {
                hash.put(key, value != null ? JSONUtil.toJsonStr(value) : null);
            }
        });
        stringRedisTemplate.opsForHash().putAll(CACHE_SHOP_KEY + id, hash);
    }*/

    @Override
    @Transactional
    public Result update(Shop shop) {
        if (shop.getId() == null) {
            return Result.fail("店铺id不能为空");
        }
        updateById(shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        if (x == null || y == null) {
            // 根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().geoRadius(
                key,
                new Circle(x, y, 5000),
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeDistance().limit(end)
        );
        if (results == null) {
            return Result.ok();
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            return Result.ok();
        }
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            String shopId = result.getContent().getName();
            ids.add(Long.valueOf(shopId));
            Distance distance = result.getDistance();
            distanceMap.put(shopId, distance);
        });
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        shops.forEach(shop -> {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        });
        return Result.ok(shops);
    }
}
