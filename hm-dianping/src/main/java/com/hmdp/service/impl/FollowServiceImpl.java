package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.apache.ibatis.annotations.Lang;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.print.attribute.standard.JobKOctets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author ls
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {



    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

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
            boolean isSuccess = save(follow);

            //todo redis 实现共同关注，在关注的时候维护set集合  k:当前用户id v 被关注的用户id
            if (isSuccess){
                String key =  "follows:" + userId;
                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }


        }else {
            //取关  删除数据
            LambdaQueryWrapper<Follow> followLambdaQueryWrapper = new LambdaQueryWrapper<>();
            LambdaQueryWrapper<Follow> queryWrapper = followLambdaQueryWrapper.eq(Follow::getFollowUserId, userId)
                    .eq(Follow::getFollowUserId, followUserId);
            boolean isSuccess = remove(queryWrapper);

            //todo redis 实现共同关注，在关注的时候维护set集合  k:当前用户id v 被关注的用户id
            if (isSuccess){

                String key =  "follows:" + userId;
                stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
            }
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

    /**
     * 共同关注
     * @param followUserId  被查看关注的id
     * @return
     */
    @Override
    public Result commonFollow(Long followUserId) {
        //当前用户的关注
        Long userId = UserHolder.getUser().getId();
        String key =  "follows:" + userId;
        //当前用户
        String key2 =  "follows:" + userId;

        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);

        if (intersect ==null || intersect.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //解析出id String-> Long
        List<Long> collect = intersect.stream().map(Long::valueOf).collect(Collectors.toList());

        //查询用户
        List<UserDTO> userDTOList = userService.listByIds(collect)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOList);
    }
}
