import com.ls.redis.utils.JedisConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisRedirectionException;

public class jredisTest {

    private Jedis jedis;

    @BeforeEach
    void setUp() {
        //建立连接
//        jedis = new Jedis("192.168.231.130", 6379);
        jedis = JedisConnectionFactory.getJedis();

        //密码认证，由于没有设置redis密码就
//      jedis.auth();

        //选择redis库 0-15
        jedis.select(0);
    }

    @Test
    void testString() {
        String result = jedis.set("name", "lishuo");
        System.out.println("result = " + result);
        String value = jedis.get("name");
        System.out.println("value = " + value);

    }

    @AfterEach
    void tearDown() {
        if (jedis != null){
            jedis.close();
        }
    }
}
