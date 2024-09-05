package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @author ls
 */
public interface IBlogService extends IService<Blog> {

    Result likeBlog(Long id);

    Result queryBlogOfFollow(Long max, Integer offset);
}
