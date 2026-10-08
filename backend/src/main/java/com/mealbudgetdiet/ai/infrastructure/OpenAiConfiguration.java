package com.mealbudgetdiet.ai.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OpenAiProperties.class)
public class OpenAiConfiguration {
}
