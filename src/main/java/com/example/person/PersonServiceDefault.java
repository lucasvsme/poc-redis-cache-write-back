package com.example.person;

import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PersonServiceDefault implements PersonService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PersonServiceDefault.class);

    private static final String WRITE_BACK_CACHE_KEY = "person:write_back";

    private final PersonRepository personRepository;

    private final RedisTemplate<String, Person> personRedisTemplate;

    public PersonServiceDefault(PersonRepository personRepository,
                                RedisTemplate<String, Person> personRedisTemplate) {
        this.personRepository = personRepository;
        this.personRedisTemplate = personRedisTemplate;
    }

    @Override
    @Transactional
    public Person create(String name, Integer age) {
        final var person = new Person();
        person.setId(UUID.randomUUID());
        person.setName(name);
        person.setAge(age);

        personRedisTemplate.boundValueOps(person.getId().toString()).set(person);
        personRedisTemplate.boundSetOps(WRITE_BACK_CACHE_KEY).add(person);
        LOGGER.info("Person cached (key={}, value={})", person.getId(), person);

        return person;
    }

    @Override
    public Person findOne(UUID personId) throws PersonNotFoundException {
        final var personOnCache = personRedisTemplate.boundValueOps(personId.toString()).get();
        if (personOnCache != null) {
            LOGGER.info("Person retrieved from cache (personId={})", personId);
            return personOnCache;
        }

        final var personNotCached = personRepository.findById(personId);
        if (personNotCached.isPresent()) {
            LOGGER.info("Person retrieved from database (personId={})", personId);

            final var person = personNotCached.get();
            personRedisTemplate.boundValueOps(person.getId().toString()).set(person);
            personRedisTemplate.boundSetOps(WRITE_BACK_CACHE_KEY).add(person);
            LOGGER.info("Person cached (key={}, value={})", personId, person);

            return person;
        }

        LOGGER.info("Person not found (personId={})", personId);
        throw new PersonNotFoundException(personId);
    }

    @Scheduled(fixedRateString = "${person-service.cache.write-back-rate}")
    public void writeBack() {
        final var amountOfPeopleToPersist = personRedisTemplate.boundSetOps(WRITE_BACK_CACHE_KEY).size();
        if (amountOfPeopleToPersist == null || amountOfPeopleToPersist == 0) {
            LOGGER.info("None people to write back from cache to database");
            return;
        }

        LOGGER.info("Found {} people to write back from cache to database", amountOfPeopleToPersist);
        final var setOperations = personRedisTemplate.boundSetOps(WRITE_BACK_CACHE_KEY);
        final var scanOptions = ScanOptions.scanOptions().build();

        try (final var cursor = setOperations.scan(scanOptions)) {
            assert cursor != null;
            while (cursor.hasNext()) {
                final var person = cursor.next();
                personRepository.save(person);
                LOGGER.info("Person saved (person={})", person);

                personRedisTemplate.boundSetOps(WRITE_BACK_CACHE_KEY).remove(person);
                LOGGER.info("Person removed from {} set (person={})", WRITE_BACK_CACHE_KEY, person);
            }
            LOGGER.info("Persisted {} people in the database", amountOfPeopleToPersist);
        } catch (RuntimeException exception) {
            LOGGER.error("Error reading {} set from Redis", WRITE_BACK_CACHE_KEY, exception);
        }
    }
}
