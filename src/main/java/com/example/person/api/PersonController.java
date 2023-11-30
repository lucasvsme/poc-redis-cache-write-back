package com.example.person.api;

import com.example.person.PersonNotFoundException;
import com.example.person.PersonService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@ControllerAdvice
@RestController
@RequestMapping("/people")
public class PersonController {

    private final PersonService personService;

    public PersonController(PersonService personService) {
        this.personService = personService;
    }

    @PostMapping
    public ResponseEntity<PersonRequest> createPerson(@Valid @RequestBody PersonRequest request,
                                                      UriComponentsBuilder uriComponentsBuilder) {
        final var person = personService.create(
                request.getName(),
                request.getAge()
        );

        final var personUri = uriComponentsBuilder.path("/{id}")
                .build(person.getId());

        return ResponseEntity.created(personUri)
                .build();
    }

    @GetMapping("/{personId}")
    public ResponseEntity<PersonResponse> findOnePerson(@PathVariable UUID personId) {
        final var person = personService.findOne(personId);

        final var personResponse = PersonResponse.builder()
                .id(person.getId())
                .name(person.getName())
                .age(person.getAge())
                .build();

        return ResponseEntity.status(HttpStatusCode.valueOf(200))
                .body(personResponse);
    }

    @ExceptionHandler(PersonNotFoundException.class)
    public ResponseEntity<ProblemDetail> personNotFoundException(PersonNotFoundException exception) {
        final var responseBody = ProblemDetail.forStatus(404);
        responseBody.setTitle("Person not found by ID");
        responseBody.setDetail("No person with ID " + exception.getPersonId() + " exists");

        return ResponseEntity.status(responseBody.getStatus())
                .body(responseBody);
    }
}
