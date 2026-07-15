package ru.yandex.practicum.filmorate.storage.user;

import ru.yandex.practicum.filmorate.model.User;

import java.util.Collection;
import java.util.Optional;

public interface UserStorage {
    User add(User user);

    User update(User user);

    void delete(long id);

    Optional<User> findById(long id);

    Collection<User> findAll();

    void addFriend(long userId, long friendId);

    void removeFriend(long userId, long friendId);
}
