package dev.stephenpearson.ztm_tracker_api_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

  @Bean
  public RedisTemplate<String, byte[]> redisTemplate(RedisConnectionFactory cf) {
    RedisTemplate<String, byte[]> template = new RedisTemplate<>();
    template.setConnectionFactory(cf);
    template.setKeySerializer(new StringRedisSerializer());
    template.setHashKeySerializer(new StringRedisSerializer());
    template.setValueSerializer(RedisSerializer.byteArray());
    template.setHashValueSerializer(RedisSerializer.byteArray());
    template.afterPropertiesSet();
    return template;
  }
}

