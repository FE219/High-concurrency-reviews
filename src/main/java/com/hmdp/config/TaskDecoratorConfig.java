package com.hmdp.config;

import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;

@Configuration
public class TaskDecoratorConfig {

    @Bean
    public TaskDecorator userContextTaskDecorator() {
        return (runnable) -> {
            UserDTO user = UserHolder.getUser();
            return () -> {
                try {
                    if (user != null) {
                        UserHolder.saveUser(user);
                    }
                    runnable.run();
                } finally {
                    UserHolder.removeUser();
                }
            };
        };
    }
}
