package com.example.person;

import java.io.Serial;
import java.util.UUID;

public final class PersonNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 8598305696033350916L;

    private final UUID personId;

    public PersonNotFoundException(UUID personId) {
        super("Person not found with ID " + personId, null);
        this.personId = personId;
    }

    public UUID getPersonId() {
        return personId;
    }
}
