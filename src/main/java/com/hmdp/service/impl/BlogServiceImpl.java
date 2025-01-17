package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;

import org.apache.tomcat.util.buf.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

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
    @Autowired
    private IFollowService followService;

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

    // 查询博客
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

    // 点赞
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

    // 查询点赞用户
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

    // 保存
    @Override
    @Transactional
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean save = save(blog);
        if (!save) {
            return Result.fail("新增笔记失败");
        }
        // 查询笔记作者的所有粉丝，将笔记保存到收件箱
        List<Follow> followId = followService.query().eq("follow_user_id", user.getId()).list();
        followId.forEach(follow -> {
            stringRedisTemplate.opsForZSet()
                    .add(FEED_KEY + follow.getUserId(), String.valueOf(blog.getId()), System.currentTimeMillis());
        });
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples =
                stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        List<String> ids = new ArrayList<>(typedTuples.size());
        Long minTime = 0L;
        int os = 1;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            ids.add(typedTuple.getValue());
            if (typedTuple.getScore().longValue() != minTime) {
                minTime = typedTuple.getScore().longValue();
                os = 1;
            } else {
                os++;
            }
        }
        List<Blog> blogs = query().in("id", ids).last("order by field(id," + StringUtils.join(ids, ',') + ")").list();
        blogs.forEach(blog -> {
            queryBlogUser(blog.getUserId(), blog);
            if (isBlogLiked(blog.getId())) {
                blog.setIsLike(true);
            }
        });
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(os);
        scrollResult.setMinTime(minTime);
        return Result.ok(scrollResult);
    }

    // 判断是否点赞
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

    // 查询用户
    private void queryBlogUser(Long blog, Blog blog1) {
        User user = userService.getById(blog);
        blog1.setName(user.getNickName());
        blog1.setIcon(user.getIcon());
    }
}
