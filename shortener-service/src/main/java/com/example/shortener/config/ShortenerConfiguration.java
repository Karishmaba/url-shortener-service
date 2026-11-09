package com.example.shortener.config;

import com.example.shortener.domain.CodeGenerator;
import com.example.shortener.domain.SecureRandomCodeGenerator;
import com.example.shortener.domain.UrlValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ShortenerConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public UrlValidator urlValidator() {
        return new UrlValidator();
    }

    @Bean
    @ConditionalOnMissingBean(CodeGenerator.class)
    public CodeGenerator codeGenerator() {
        return new SecureRandomCodeGenerator();
    }

    @Bean
    public PublicBaseUrl publicBaseUrl(@Value("${shortener.public-base-url}") String value) {
        return new PublicBaseUrl(value);
    }
}
