package ru.yandex.practicum.filmorate.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
    public UserService(UserStorage userStorage) {
        this.userStorage = userStorage;
    }

    public List<User> findAll() {
        log.info("Showed all users");
        return userStorage.findAll().stream().toList();
    }

    public User findById(int id) {
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

    public void delete(int id) {
        getUserOrThrow(id);
        userStorage.delete(id);
        log.info("User deleted: id={}", id);
    }

    public void addFriend(int userId, int friendId) {
        if (userId == friendId) {
            log.warn("User cannot add themselves as a friend: userId={}", userId);
            throw new ValidationException("User cannot add themselves as a friend");
        }
        User user = getUserOrThrow(userId);
        User friend = getUserOrThrow(friendId);
        user.getFriends().add((long) friendId);
        friend.getFriends().add((long) userId);
        log.info("Users {} and {} are now friends", userId, friendId);
    }

    public void removeFriend(int userId, int friendId) {
        User user = getUserOrThrow(userId);
        User friend = getUserOrThrow(friendId);
        user.getFriends().remove((long) friendId);
        friend.getFriends().remove((long) userId);
        log.info("Users {} and {} are no longer friends", userId, friendId);
    }

    public List<User> getFriends(int userId) {
        log.info("Showed friends of user {}", userId);
        User user = getUserOrThrow(userId);
        return user.getFriends().stream()
                .map(friendId -> getUserOrThrow(friendId.intValue()))
                .sorted(Comparator.comparingInt(User::getId))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public List<User> getCommonFriends(int userId, int otherId) {
        log.info("Showed common friends of users {} and {}", userId, otherId);
        User user = getUserOrThrow(userId);
        User other = getUserOrThrow(otherId);
        Set<Long> commonFriendIds = new HashSet<>(user.getFriends());
        commonFriendIds.retainAll(other.getFriends());
        return commonFriendIds.stream()
                .map(friendId -> getUserOrThrow(friendId.intValue()))
                .sorted(Comparator.comparingInt(User::getId))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private User getUserOrThrow(int id) {
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
