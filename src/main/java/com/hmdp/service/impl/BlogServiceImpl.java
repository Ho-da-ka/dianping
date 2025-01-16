package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Autowired
    private IUserService userService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            Long userId = blog.getUserId();
            queryBlogUser(userId, blog);
            if (isBlogLiked(blog.getId())) {
                blog.setIsLike(true);
            }
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在");
        }
        if (blog.getUserId() != null) {
            queryBlogUser(blog.getUserId(), blog);
        }
        if (isBlogLiked(id)) {
            blog.setIsLike(true);
        }
        return Result.ok(blog);
    }

    @Override
    public Result likeBlog(Long id) {
        UserDTO user = UserHolder.getUser();
        Long userId = UserHolder.getUser().getId();
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, String.valueOf(userId));
        if (score != null) {
            // 已点赞，取消点赞
            stringRedisTemplate.opsForZSet().remove(key, String.valueOf(userId));
            update().setSql("liked = liked - 1").eq("id", id).update();
        } else {
            // 未点赞，点赞
            stringRedisTemplate.opsForZSet().add(key, String.valueOf(userId), System.currentTimeMillis());
            update().setSql("liked = liked + 1").eq("id", id).update();
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        List<UserDTO> users = new ArrayList<>();
        String key = BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        List<Long> list = new ArrayList<>();
        if (top5 != null) {
            top5.forEach(userId -> {
                list.add(Long.valueOf(userId));
            });
        }
        List<User> userList = userService.query().in("id", list).list();
        userList.forEach(user -> {
            UserDTO userDTO = new UserDTO();
            BeanUtils.copyProperties(user, userDTO);
            users.add(userDTO);
        });
        return Result.ok(users);
    }

    private boolean isBlogLiked(Long id) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return false;
        }
        Long userId = user.getId();
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, String.valueOf(userId));
        return score != null;
    }

    private void queryBlogUser(Long blog, Blog blog1) {
        User user = userService.getById(blog);
        blog1.setName(user.getNickName());
        blog1.setIcon(user.getIcon());
    }
}
