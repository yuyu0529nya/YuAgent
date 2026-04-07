package org.xhy;

import org.dromara.x.file.storage.spring.EnableFileStorage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 应用入口类 */
@SpringBootApplication
@EnableScheduling
@EnableFileStorage
public class YuAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(YuAgentApplication.class, args);
    }
}
