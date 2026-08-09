package com.bma.common.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * QueryDSL {@link JPAQueryFactory} 등록.
 *
 * <p>{@code @PersistenceContext}로 주입된 EntityManager는 실제로는 프록시라
 * 트랜잭션마다 올바른 영속성 컨텍스트로 연결된다. 따라서 팩토리를 싱글턴으로 두어도 안전하다.</p>
 */
@Configuration
public class QuerydslConfig {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 동적 쿼리 작성을 위한 QueryDSL 팩토리.
     *
     * @return JPA 쿼리 팩토리
     */
    @Bean
    public JPAQueryFactory jpaQueryFactory() {
        return new JPAQueryFactory(entityManager);
    }
}
