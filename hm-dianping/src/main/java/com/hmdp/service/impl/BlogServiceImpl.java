package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.entity.Blog;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * @author ls
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;


    /**
     * 点赞功能
     * @param id
     * @return
     */
    @Override
    public Result likeBlog(Long id) {
        //判断当前登录用户 是否已经点赞
        Long userId = UserHolder.getUser().getId();
        //查看redis 中set集合  以blog：id 为 key   以用户id为v的集合存不存在

        String key = "blog:liked:" + id;
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        if (Boolean.FALSE.equals(isMember)) {
              //未点赞，可以点赞
                    // 数据库点赞 + 1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            //将用户id 存放在管理点赞的set集合中
            if (isSuccess){
                stringRedisTemplate.opsForSet().add(key,userId.toString());
            }
        }else {

            //已经点赞 取消点赞
            //数据库点在 - 1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();

            if (isSuccess){
                //管理点赞的set集合中 删除用户id
                stringRedisTemplate.opsForSet().remove(key,userId.toString());
            }
        }
        return Result.ok();

    }


    /**
     * 滚动分页获取关注的人的发送
     * @param max
     * @param offset
     * @return
     */
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //当前用户信息
        Long userId = UserHolder.getUser().getId();
        //查看当前用户的被推送 zrevrangebySCore key max min Limit offset count
        String key = FEED_KEY + userId;

        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        //非空判断
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }

        //数据解析-》
        // 为下一次分页的参数获取，这一次最后的时间戳（最小的时间戳）作为下一次查询最大的时间戳(max)
        // 最小时间戳出现的次数作为下次查询的偏移量
        // blogids 作为参数查询bloglist 返回数据

        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int count = 1;
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            //获取id
            String idStr = tuple.getValue();
            ids.add(Long.valueOf(idStr));
            //时间戳
             Long time =  tuple.getScore().longValue();
             if (time == minTime){
                 count++;
             }else {
                 count = 1;
                 minTime =time;
             }
        }

        //根据id 查询blog

//        List<Blog> blogs = listByIds(ids); 注意在mp中这个方法其实就是in查询，返回的结果时无序的

        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();


        //todo 查询博文有关信息
//        for (Blog blog : blogs) {
//            // 5.1.查询blog有关的用户
//            queryBlogUser(blog);
//            // 5.2.查询blog是否被点赞
//            isBlogLiked(blog);
//        }



        //包装返回结果

        ScrollResult r = new ScrollResult();
        r.setList(blogs);
        r.setOffset(count);
        r.setMinTime(minTime);


        return Result.ok(r);
    }
}
