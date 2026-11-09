package com.example.shortener.integration;

import com.example.shortener.ShortenerApplication;
import com.example.shortener.domain.CodeGenerator;
import com.example.shortener.persistence.LinkRepository;
import com.zaxxer.hikari.HikariDataSource;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.ApplicationContextFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Boots the real application without a listening socket or destination requests. */
final class TestApplication implements AutoCloseable {
    final ConfigurableApplicationContext context;
    final MockMvc mvc;
    final JsonMapper json;
    final JdbcTemplate jdbc;
    final LinkRepository repository;

    static TestApplication fresh() {
        return new TestApplication(memoryDatabase(), null);
    }

    static String memoryDatabase() {
        return "jdbc:h2:mem:it_" + UUID.randomUUID().toString().replace("-", "")
                + ";DB_CLOSE_DELAY=0;LOCK_TIMEOUT=10000";
    }

    TestApplication(String database, CodeGenerator generator) {
        context = new SpringApplicationBuilder(ShortenerApplication.class)
                .web(WebApplicationType.SERVLET)
                .contextFactory(ApplicationContextFactory.of(() -> {
                    GenericWebApplicationContext application = new GenericWebApplicationContext();
                    application.setServletContext(new MockServletContext());
                    return application;
                }))
                .initializers(application -> {
                    if (generator != null) {
                        application.getBeanFactory().registerSingleton("testCodeGenerator", generator);
                    }
                })
                .run("--spring.datasource.url=" + database,
                        "--spring.datasource.username=sa", "--spring.datasource.password=",
                        "--shortener.public-base-url=https://sho.rt:9443/",
                        "--spring.main.banner-mode=off", "--logging.level.root=ERROR");
        mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).build();
        json = context.getBean(JsonMapper.class);
        jdbc = context.getBean(JdbcTemplate.class);
        repository = context.getBean(LinkRepository.class);
    }

    MockHttpServletResponse request(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        MockHttpServletResponse response = mvc.perform(request).andReturn().getResponse();
        assertEquals(expectedStatus, response.getStatus(), response.getContentAsString());
        return response;
    }

    JsonNode body(MockHttpServletResponse response) throws Exception {
        assertNotNull(response.getContentType());
        assertTrue(response.getContentType().startsWith("application/json"));
        return json.readTree(response.getContentAsString());
    }

    MockHttpServletRequestBuilder creationRequest(String destination) {
        return post("/api/urls").contentType("application/json")
                .content(json.writeValueAsString(Map.of("url", destination)));
    }

    JsonNode create(String destination) throws Exception {
        return body(request(creationRequest(destination), 201));
    }

    long count(String code) throws Exception {
        JsonNode stats = body(request(get("/api/urls/{code}/stats", code), 200));
        assertEquals(Set.of("code", "redirectCount"), new HashSet<>(stats.propertyNames()));
        assertEquals(code, stats.path("code").stringValue());
        return stats.path("redirectCount").longValue();
    }

    void error(MockHttpServletRequestBuilder request, int status, String code) throws Exception {
        JsonNode error = body(request(request, status));
        assertEquals(code, error.path("code").stringValue());
        assertFalse(error.path("message").stringValue().isBlank());
        Set<String> fields = code.equals("INVALID_URL")
                ? Set.of("code", "message", "field") : Set.of("code", "message");
        assertEquals(fields, new HashSet<>(error.propertyNames()));
        if (code.equals("INVALID_URL")) {
            assertEquals("url", error.path("field").stringValue());
        }
        assertFalse(error.path("message").stringValue().contains("jdbc:"));
    }

    long rowCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM short_links", Long.class);
    }

    HikariDataSource dataSource() {
        return context.getBean(HikariDataSource.class);
    }

    @Override
    public void close() {
        context.close();
    }
}
