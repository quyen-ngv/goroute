package com.ds.goroute.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Every mapper XML must parse as XML before MyBatis ever sees it. A malformed one (a {@code --}
 * inside a comment, say) only surfaces when the SqlSessionFactory is built, which on 2026-11-12
 * meant a production image that compiled, pushed, and then failed to start.
 */
class MapperXmlWellFormedTest {

    @Test
    void everyMapperXmlParses() throws Exception {
        Resource[] mappers = new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/*.xml");
        assertThat(mappers).isNotEmpty();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // The MyBatis DTD is not fetched over the network.
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        for (Resource mapper : mappers) {
            assertThatCode(() -> {
                try (InputStream input = mapper.getInputStream()) {
                    factory.newDocumentBuilder().parse(input);
                }
            }).as(mapper.getFilename()).doesNotThrowAnyException();
        }
    }
}
