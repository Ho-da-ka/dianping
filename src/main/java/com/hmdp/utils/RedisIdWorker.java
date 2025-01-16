package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class RedisIdWorker {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final long BEGIN_TIMESTAMP = 1640995200L;

    private static final long COUNT_BITS = 32;

    public long nextId(String keyPrefix) {
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(java.time.ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;
        String date = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        return timestamp << COUNT_BITS | count;
    }

}
