package ru.yandex.practicum.filmorate.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.exception.ValidationException;
import ru.yandex.practicum.filmorate.model.User;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/users")
public class UserController {
    private final Map<Integer, User> users = new HashMap<>();
    private int idCounter = 1;

    @GetMapping
    public List<User> findAll() {
        log.info("Showed all users");
        return new ArrayList<>(users.values());
    }

    @PostMapping
    public User create(@RequestBody User user) {
        validateAndPrepare(user);
        user.setId(idCounter++);
        users.put(user.getId(), user);
        log.info("User created: {}", user);
        return user;
    }

    @PutMapping
    public User update(@RequestBody User user) {
        if (!users.containsKey(user.getId())) {
            log.warn("User not found: {}", user.getId());
            throw new NotFoundException("User with id " + user.getId() + " not found");
        }
        validateAndPrepare(user);
        users.put(user.getId(), user);
        log.info("User updated: {}", user);
        return user;
    }

    private void validateAndPrepare(User user) {
        validate(user);
        if (user.getName() == null || user.getName().isBlank()) {
            user.setName(user.getLogin());
        }
    }

    private void validate(User user) {
        if (user.getEmail() == null || user.getEmail().isBlank() || !user.getEmail().contains("@")) {
            log.warn("User email validation failed");
            throw new ValidationException("Email must not be empty and must contain @");
        }
        if (user.getLogin() == null || user.getLogin().isBlank() || user.getLogin().contains(" ")) {
            log.warn("User login validation failed");
            throw new ValidationException("Login must not be empty and must not contain spaces");
        }
        if (user.getBirthday() == null) {
            log.warn("User birthday validation failed");
            throw new ValidationException("Birthday must not be empty");
        }
        if (user.getBirthday().isAfter(LocalDate.now())) {
            log.warn("User birthday validation failed");
            throw new ValidationException("Birthday cannot be in the future");
        }
    }
}
