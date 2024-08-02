package com.ls;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.pojo.User;
import io.lettuce.core.StringMatchResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@SpringBootTest
class StringRedisTemplateTest {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    //序列化反序列话工具
    private static final ObjectMapper mapper = new ObjectMapper();


    @Test
    void testString(){
        ValueOperations valueOperations = stringRedisTemplate.opsForValue();
        valueOperations.set("test:stringRedis:user:1","hnsqls0");

        Object result = valueOperations.get("name0");
        System.out.println("result = " + result);
    }

    @Test
    void TestSavaUser() throws JsonProcessingException {

        User user = new User("hnsqls02",21);

        //序列化
        String json = mapper.writeValueAsString(user);

        //存入redis
        stringRedisTemplate.opsForValue()
                .set("test:stringRedis:user:2",json);

        //从redis取数据
        String json1 = stringRedisTemplate.opsForValue()
                .get("test:stringRedis:user:2");

        //反序列化
        User user1 = mapper.readValue(json1, User.class);
        System.out.println("user1 = " + user1);

    }


}