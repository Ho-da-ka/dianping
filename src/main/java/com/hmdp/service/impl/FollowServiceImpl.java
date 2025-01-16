package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private UserServiceImpl userService;

    @Override
    @Transactional
    public Result follow(Long followId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        if (isFollow) {
            // 关注
            save(new Follow()
                    .setUserId(userId)
                    .setFollowUserId(followId));
            stringRedisTemplate.opsForSet().add("follows:" + userId, followId.toString());
        } else {
            // 取关
            remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followId));
            stringRedisTemplate.opsForSet().remove("follows:" + userId, followId.toString());
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followId) {
        Long userId = UserHolder.getUser().getId();
        if (query().eq("user_id", userId).eq("follow_user_id", followId).count() > 0) {
            return Result.ok(true);
        }
        return Result.ok(false);
    }

    @Override
    public Result followCommons(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key1 = "follows:" + userId;
        String key2 = "follows:" + id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok(0);
        }
        List<UserDTO> userDTOS = new ArrayList<>(intersect.size());
        userService.listByIds(intersect).forEach(user -> {
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            userDTOS.add(userDTO);
        });
        return Result.ok(userDTOS);
    }
}
