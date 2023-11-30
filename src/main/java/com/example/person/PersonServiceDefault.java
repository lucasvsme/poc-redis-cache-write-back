package com.example.person;

import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PersonServiceDefault implements PersonService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PersonServiceDefault.class);

    private final PersonServiceCacheProperties cacheProperties;
    private final PersonRepository personRepository;

    private final RedisTemplate<String, Person> personRedisTemplate;

    public PersonServiceDefault(PersonServiceCacheProperties cacheProperties,
                                PersonRepository personRepository,
                                RedisTemplate<String, Person> personRedisTemplate) {
        this.cacheProperties = cacheProperties;
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
        personRedisTemplate.boundSetOps(cacheProperties.getWriteBackKey()).add(person);
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
            personRedisTemplate.boundSetOps(cacheProperties.getWriteBackKey()).add(person);
            LOGGER.info("Person cached (key={}, value={})", personId, person);

            return person;
        }

        LOGGER.info("Person not found (personId={})", personId);
        throw new PersonNotFoundException(personId);
    }
}
