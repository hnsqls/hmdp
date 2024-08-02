package com.ls.config.redis;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisSentinelConnection;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory){

        RedisTemplate<String, Object> stringObjectRedisTemplate = new RedisTemplate<>();


        //创建连接工厂  RedisConnectionFactory redisConnectionFactory = new RedisConnectionFactory();
//        RedisConnectionFactory redisConnectionFactory = new LettuceConnectionFactory();
       //RedisConnectionFactory redisConnectionFactory = new JedisConnectionFactory();

        //设置连接工厂
        stringObjectRedisTemplate.setConnectionFactory(redisConnectionFactory);

        //创建序列化器
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer genericJackson2JsonRedisSerializer = new GenericJackson2JsonRedisSerializer();

        //设置序列化器
        stringObjectRedisTemplate.setKeySerializer(stringRedisSerializer);
        stringObjectRedisTemplate.setHashKeySerializer(stringRedisSerializer);
        stringObjectRedisTemplate.setValueSerializer(genericJackson2JsonRedisSerializer);
        stringObjectRedisTemplate.setHashValueSerializer(genericJackson2JsonRedisSerializer);

        return stringObjectRedisTemplate;
    }
}
