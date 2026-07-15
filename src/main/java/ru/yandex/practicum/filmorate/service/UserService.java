package ru.yandex.practicum.filmorate.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.exception.ValidationException;
import ru.yandex.practicum.filmorate.model.User;
import ru.yandex.practicum.filmorate.storage.user.UserStorage;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserService {
    private final UserStorage userStorage;

    @Autowired
    public UserService(@Qualifier("userDbStorage") UserStorage userStorage) {
        this.userStorage = userStorage;
    }

    public List<User> findAll() {
        log.info("Showed all users");
        return userStorage.findAll().stream().toList();
    }

    public User findById(long id) {
        log.info("Showed user with id {}", id);
        return getUserOrThrow(id);
    }

    public User create(User user) {
        validateAndPrepare(user);
        User created = userStorage.add(user);
        log.info("User created: {}", created);
        return created;
    }

    public User update(User user) {
        User existing = getUserOrThrow(user.getId());
        validateAndPrepare(user);
        user.setFriends(existing.getFriends());
        User updated = userStorage.update(user);
        log.info("User updated: {}", updated);
        return updated;
    }

    public void delete(long id) {
        getUserOrThrow(id);
        userStorage.delete(id);
        log.info("User deleted: id={}", id);
    }

    public void addFriend(long userId, long friendId) {
        validateDifferentUsers(userId, friendId);
        getUserOrThrow(userId);
        getUserOrThrow(friendId);
        userStorage.addFriend(userId, friendId);
        log.info("User {} added user {} as friend", userId, friendId);
    }

    public void removeFriend(long userId, long friendId) {
        validateDifferentUsers(userId, friendId);
        getUserOrThrow(userId);
        getUserOrThrow(friendId);
        userStorage.removeFriend(userId, friendId);
        log.info("User {} removed user {} from friends", userId, friendId);
    }

    public List<User> getFriends(long userId) {
        log.info("Showed friends of user {}", userId);
        User user = getUserOrThrow(userId);
        return user.getFriends().stream()
                .map(friendId -> getUserOrThrow(friendId.intValue()))
                .sorted(Comparator.comparingLong(User::getId))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public List<User> getCommonFriends(long userId, long otherId) {
        validateDifferentUsers(userId, otherId);
        log.info("Showed common friends of users {} and {}", userId, otherId);
        User user = getUserOrThrow(userId);
        User other = getUserOrThrow(otherId);
        Set<Long> commonFriendIds = new HashSet<>(user.getFriends());
        commonFriendIds.retainAll(other.getFriends());
        return commonFriendIds.stream()
                .map(this::getUserOrThrow)
                .sorted(Comparator.comparingLong(User::getId))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private void validateDifferentUsers(long userId, long otherUserId) {
        if (userId == otherUserId) {
            log.warn("Users must be different: userId={}", userId);
            throw new ValidationException("Users must be different");
        }
    }

    private User getUserOrThrow(long id) {
        return userStorage.findById(id)
                .orElseThrow(() -> {
                    log.warn("User not found: {}", id);
                    return new NotFoundException("User with id " + id + " not found");
                });
    }

    private void validateAndPrepare(User user) {
        validate(user);
        if (user.getName() == null || user.getName().isBlank()) {
            user.setName(user.getLogin());
        }
        if (user.getFriends() == null) {
            user.setFriends(new HashSet<>());
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
