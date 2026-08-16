package com.bma;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 애플리케이션 진입점.
 *
 * <p>JPA Auditing 활성화는 {@code AuditorAware} 빈과 함께 두는 편이 응집도가 높아
 * {@link com.bma.common.config.JpaAuditConfig}로 옮겼다.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BlindMatchingApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlindMatchingApiApplication.class, args);
    }
}
