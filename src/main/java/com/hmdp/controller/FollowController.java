package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Autowired
    private IFollowService followService;

    @PutMapping("/{followId}/{isFollow}")
    public Result follow(@PathVariable("followId") Long followId, @PathVariable("isFollow") Boolean isFollow) {
        // 关注功能
        return followService.follow(followId, isFollow);
    }

    @GetMapping("/or/not/{followId}")
    public Result isFollow(@PathVariable("followId") Long followId) {
        // 查询是否关注功能
        return followService.isFollow(followId);
    }
    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable("id") Long id){
        // 查询共同关注功能
        return followService.followCommons(id);
    }
}
