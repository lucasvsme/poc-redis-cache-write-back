package com.example.person;

import java.util.UUID;

public interface PersonService {

    Person create(String name, Integer age);

    Person findOne(UUID personId) throws PersonNotFoundException;
}
