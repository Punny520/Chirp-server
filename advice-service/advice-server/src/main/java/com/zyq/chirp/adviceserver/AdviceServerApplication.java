package com.zyq.chirp.adviceserver;

import com.zyq.chirp.authclient.client.AuthClient;
import com.zyq.chirp.chirpclient.client.ChirperClient;
import com.zyq.chirp.common.mq.DefaultKafkaProducer;
import com.zyq.chirp.userclient.client.UserClient;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@EnableFeignClients(basePackageClasses = {ChirperClient.class, UserClient.class, AuthClient.class})
@Import({DefaultKafkaProducer.class})
@EnableCaching
@MapperScan("com.zyq.chirp.adviceserver.mapper")
public class AdviceServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdviceServerApplication.class);
    }
}