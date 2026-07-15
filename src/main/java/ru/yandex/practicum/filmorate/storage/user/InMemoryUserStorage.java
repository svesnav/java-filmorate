package ru.yandex.practicum.filmorate.storage.user;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.filmorate.model.User;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
public class InMemoryUserStorage implements UserStorage {
    private final Map<Long, User> users = new HashMap<>();
    private int idCounter = 1;

    @Override
    public User add(User user) {
        user.setId(idCounter++);
        users.put(user.getId(), user);
        log.debug("User added to storage: {}", user);
        return user;
    }

    @Override
    public User update(User user) {
        users.put(user.getId(), user);
        log.debug("User updated in storage: {}", user);
        return user;
    }

    @Override
    public void delete(long id) {
        users.remove(id);
        log.debug("User deleted from storage: id={}", id);
    }

    @Override
    public Optional<User> findById(long id) {
        return Optional.ofNullable(users.get(id));
    }

    @Override
    public Collection<User> findAll() {
        return users.values();
    }

    @Override
    public void addFriend(long userId, long friendId) {
        users.get(userId).getFriends().add(friendId);
    }

    @Override
    public void removeFriend(long userId, long friendId) {
        users.get(userId).getFriends().remove(friendId);
    }

    @Override
    public List<User> getFriends(long userId) {
        return users.get(userId).getFriends().stream()
                .map(users::get)
                .sorted(Comparator.comparingLong(User::getId))
                .toList();
    }

    @Override
    public List<User> getCommonFriends(long userId, long otherId) {
        Set<Long> commonFriendIds = new HashSet<>(users.get(userId).getFriends());
        commonFriendIds.retainAll(users.get(otherId).getFriends());
        return commonFriendIds.stream()
                .map(users::get)
                .sorted(Comparator.comparingLong(User::getId))
                .toList();
    }
}
