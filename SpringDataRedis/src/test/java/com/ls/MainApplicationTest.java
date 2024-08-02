package com.ls;

import com.ls.pojo.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MainApplicationTest {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;


    @Test
    void testString(){
        ValueOperations valueOperations = redisTemplate.opsForValue();
        valueOperations.set("name0","hnsqls0");

        Object result = valueOperations.get("name0");
        System.out.println("result = " + result);
    }

    @Test
    void TestSavaUser(){
        redisTemplate.opsForValue()
                .set("test:user:100",new User("hnsqls",21));

        User o = (User) redisTemplate.opsForValue().get("test:user:100");
        System.out.println("o = " + o);

    }


}