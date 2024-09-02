package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * @author ls
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {




    /**
     * 关注或取关
     * @param followUserId
     * @param isFollow
     * @return
     */
    @Override
    public Result follow(Long followUserId, boolean isFollow) {
        //获取用户信息
        Long userId = UserHolder.getUser().getId();
        //判断是否是关注还是取关
        if (isFollow){
            //关注 新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            save(follow);
        }else {
            //取关  删除数据
            LambdaQueryWrapper<Follow> followLambdaQueryWrapper = new LambdaQueryWrapper<>();
            LambdaQueryWrapper<Follow> queryWrapper = followLambdaQueryWrapper.eq(Follow::getFollowUserId, userId)
                    .eq(Follow::getFollowUserId, followUserId);
            remove(queryWrapper);
        }

        return Result.ok();
    }


    /**
     *判断用户是否关注
     * 查tb_follow 表 粗在就是关注了，不存在就是没有关注
     * @param followUserId
     * @return
     */
    @Override
    public Result isFollow(Long followUserId) {
        //用户信息
        Long userId = UserHolder.getUser().getId();

        //查tb_follow 表 粗在就是关注了，不存在就是没有关注
        LambdaQueryWrapper<Follow> followLambdaQueryWrapper = new LambdaQueryWrapper<>();
        LambdaQueryWrapper<Follow> queryWrapper = followLambdaQueryWrapper.eq(Follow::getUserId, userId)
                .eq(Follow::getFollowUserId, followUserId);

        int count = count(queryWrapper);
        return Result.ok(count > 0 );
    }
}
