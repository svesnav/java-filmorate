package ru.yandex.practicum.filmorate.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.exception.ValidationException;
import ru.yandex.practicum.filmorate.model.EventType;
import ru.yandex.practicum.filmorate.model.FeedEvent;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.model.Operation;
import ru.yandex.practicum.filmorate.model.User;
import ru.yandex.practicum.filmorate.storage.feed.FeedStorage;
import ru.yandex.practicum.filmorate.storage.film.FilmStorage;
import ru.yandex.practicum.filmorate.storage.user.UserStorage;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class UserService {
    private final UserStorage userStorage;
    private final FilmStorage filmStorage;
    private final FeedStorage feedStorage;

    @Autowired
    public UserService(@Qualifier("userDbStorage") UserStorage userStorage,
                       @Qualifier("filmDbStorage") FilmStorage filmStorage,
                       @Qualifier("feedDbStorage") FeedStorage feedStorage) {
        this.userStorage = userStorage;
        this.filmStorage = filmStorage;
        this.feedStorage = feedStorage;
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
        addFeedEvent(userId, Operation.ADD, friendId);
        log.info("User {} added user {} as friend", userId, friendId);
    }

    public void removeFriend(long userId, long friendId) {
        validateDifferentUsers(userId, friendId);
        getUserOrThrow(userId);
        getUserOrThrow(friendId);
        userStorage.removeFriend(userId, friendId);
        addFeedEvent(userId, Operation.REMOVE, friendId);
        log.info("User {} removed user {} from friends", userId, friendId);
    }

    public List<User> getFriends(long userId) {
        getUserOrThrow(userId);
        log.info("Showed friends of user {}", userId);
        return userStorage.getFriends(userId);
    }

    public List<User> getCommonFriends(long userId, long otherId) {
        validateDifferentUsers(userId, otherId);
        getUserOrThrow(userId);
        getUserOrThrow(otherId);
        log.info("Showed common friends of users {} and {}", userId, otherId);
        return userStorage.getCommonFriends(userId, otherId);
    }

    public List<Film> getRecommendations(long userId) {
        getUserOrThrow(userId);
        Optional<Long> similarUserIdOpt = filmStorage.findMostSimilarUserId(userId);
        if (similarUserIdOpt.isEmpty()) {
            log.info("No similar user found for user {}", userId);
            return List.of();
        }
        long similarUserId = similarUserIdOpt.get();
        log.info("Found similar user {} for user {}", similarUserId, userId);
        return filmStorage.getRecommendedFilms(userId, similarUserId);
    }

    private void validateDifferentUsers(long userId, long otherUserId) {
        if (userId == otherUserId) {
            log.warn("Users must be different: userId={}", userId);
            throw new ValidationException("Users must be different");
        }
    }

    private void addFeedEvent(long userId, Operation operation, long friendId) {
        feedStorage.add(new FeedEvent(System.currentTimeMillis(), userId, EventType.FRIEND,
                operation, 0, friendId));
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
