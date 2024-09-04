package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * @author ls
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService followService;

    /**
     * 关注或取关
     * @param followUserId
     * @param isFollow
     * @return
     */
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long followUserId,@PathVariable("isFollow") boolean isFollow){
        return followService.follow(followUserId,isFollow);
    }

    /**
     * 查看是否关注
     * @param followUserId
     * @return
     */
    @GetMapping("/or/not/{id}")
    public Result follow(@PathVariable("id") Long followUserId){
        return followService.isFollow(followUserId);
    }


    /**
     * 共同关注
     * @param followUserId
     * @return
     */
    @GetMapping("common/{id}")
    public Result commonFollow(@PathVariable("id") Long followUserId){

        return  followService.commonFollow(followUserId);
    }

}
