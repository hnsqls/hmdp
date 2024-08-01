package com.ls.redis.utils;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class JedisConnectionFactory {

    private  static  final JedisPool jedispool;

    static {
        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        //最大连接数
        jedisPoolConfig.setMaxTotal(8);
        //最大空闲连接
        jedisPoolConfig.setMaxIdle(8);
        //最小空闲连接
        jedisPoolConfig.setMinIdle(1);
        //等待时长，当没有连接可以使用，要等待多长时间
        jedisPoolConfig.setMaxWaitMillis(1000);

        jedispool = new JedisPool(jedisPoolConfig,"192.168.231.130",
                6379,1000);
    }

    public static Jedis getJedis(){
        return jedispool.getResource();
    }

}
