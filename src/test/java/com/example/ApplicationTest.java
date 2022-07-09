package com.example;

import com.example.person.api.PersonRequest;
import com.example.person.api.PersonResponse;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApplicationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES_CONTAINER =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres"));

    @Container
    private static final GenericContainer<?> REDIS_CONTAINER =
            new GenericContainer<>(DockerImageName.parse("redis"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    private static void setApplicationProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES_CONTAINER::getUsername);
        registry.add("spring.datasource.password", POSTGRES_CONTAINER::getPassword);
        registry.add("spring.redis.host", REDIS_CONTAINER::getHost);
        registry.add("spring.redis.port", REDIS_CONTAINER::getFirstMappedPort);
        registry.add("person-service.cache.write-back-rate", () -> 1000 /* milliseconds */);
    }

    private static PersonRequest personRequest;
    private static UUID personId;

    @Autowired
    private WebTestClient webTestClient;

    @AfterAll
    public static void afterAll() throws InterruptedException {
        // Delaying test suite finish to make sure we can
        // get log statements demonstrating people created
        // being written back to the relational database
        Thread.sleep(2000);
    }

    @RepeatedTest(10)
    @Order(1)
    void creatingPerson() {
        personRequest = PersonRequest.builder()
                .name("John Smith")
                .age(45)
                .build();

        final var exchangeResult = webTestClient.post()
                .uri("/people")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(personRequest))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CREATED)
                .expectHeader().exists(HttpHeaders.LOCATION)
                .expectBody().isEmpty();

        personId = getPersonIdFromLocationHeader(exchangeResult);
    }

    @RepeatedTest(10)
    @Order(2)
    void findingPersonCreatedById() {
        final var exchangeResult = webTestClient.get()
                .uri("/people/{personId}", personId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.OK)
                .expectBody(PersonResponse.class)
                .returnResult();

        final var personResponse = exchangeResult.getResponseBody();

        assertNotNull(personResponse);
        assertEquals(personId, personResponse.getId());
        assertEquals(personRequest.getName(), personResponse.getName());
        assertEquals(personRequest.getAge(), personResponse.getAge());
    }

    @Test
    void findingUnknownPersonById() {
        final var randomPersonId = UUID.randomUUID();

        webTestClient.get()
                .uri("/people/{personId}", randomPersonId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)
                .expectBody()
                .jsonPath("$.type").value(Matchers.equalTo("about:blank"), String.class)
                .jsonPath("$.title").value(Matchers.equalTo("Person not found by ID"), String.class)
                .jsonPath("$.status").value(Matchers.equalTo(404), Integer.class)
                .jsonPath("$.detail").value(Matchers.equalTo("No person with ID " + randomPersonId + " exists"), String.class)
                .jsonPath("$.instance").value(Matchers.equalTo("/people/" + randomPersonId), String.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void creatingPersonWithoutName(String name) {
        testCreatePersonWithInvalidName(name);
    }

    @Test
    void creatingPersonWithNameTooLong() {
        testCreatePersonWithInvalidName("John".repeat(126));
    }

    @Test
    void creatingPersonWithoutAge() {
        testCreatePersonWithInvalidAge(null);
    }

    @Test
    void creatingPersonTooYoung() {
        testCreatePersonWithInvalidAge(-1);
    }

    @Test
    void creatingPersonTooOld() {
        testCreatePersonWithInvalidAge(201);
    }

    private void testCreatePersonWithInvalidName(String name) {
        final var personRequest = PersonRequest.builder()
                .name(name)
                .age(45)
                .build();

        webTestClient.post()
                .uri("/people")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(personRequest))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private void testCreatePersonWithInvalidAge(Integer age) {
        final var personRequest = PersonRequest.builder()
                .name("John Smith")
                .age(age)
                .build();

        webTestClient.post()
                .uri("/people")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(personRequest))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private UUID getPersonIdFromLocationHeader(EntityExchangeResult<Void> exchangeResult) {
        final var responseHeaders = exchangeResult.getResponseHeaders();
        final var location = responseHeaders.getLocation();
        assert location != null;

        final var segments = location.toString().split("/");
        final var lastSegment = segments[segments.length - 1];

        return UUID.fromString(lastSegment);
    }
}
