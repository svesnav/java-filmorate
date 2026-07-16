package ru.yandex.practicum.filmorate.storage.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.filmorate.model.User;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Repository
@Qualifier("userDbStorage")
@RequiredArgsConstructor
public class UserDbStorage implements UserStorage {
    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<User> userRowMapper = (rs, rowNum) -> {
        User user = new User();
        user.setId(rs.getLong("user_id"));
        user.setEmail(rs.getString("email"));
        user.setLogin(rs.getString("login"));
        user.setName(rs.getString("name"));
        Date birthday = rs.getDate("birthday");
        if (birthday != null) {
            user.setBirthday(birthday.toLocalDate());
        }
        return user;
    };

    @Override
    public User add(User user) {
        String sql = "INSERT INTO users (email, login, name, birthday) VALUES (?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, user.getEmail());
            ps.setString(2, user.getLogin());
            ps.setString(3, user.getName());
            ps.setDate(4, Date.valueOf(user.getBirthday()));
            return ps;
        }, keyHolder);
        user.setId(keyHolder.getKey().longValue());
        user.setFriends(new HashSet<>());
        log.debug("User added to database: {}", user);
        return user;
    }

    @Override
    public User update(User user) {
        String sql = "UPDATE users SET email = ?, login = ?, name = ?, birthday = ? WHERE user_id = ?";
        jdbcTemplate.update(sql, user.getEmail(), user.getLogin(), user.getName(),
                Date.valueOf(user.getBirthday()), user.getId());
        log.debug("User updated in database: {}", user);
        return findById(user.getId()).orElse(user);
    }

    @Override
    public void delete(long id) {
        jdbcTemplate.update("DELETE FROM friendships WHERE user_id = ? OR friend_id = ?", id, id);
        jdbcTemplate.update("DELETE FROM film_likes WHERE user_id = ?", id);
        jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", id);
        log.debug("User deleted from database: id={}", id);
    }

    @Override
    public Optional<User> findById(long id) {
        List<User> users = jdbcTemplate.query(
                "SELECT user_id, email, login, name, birthday FROM users WHERE user_id = ?",
                userRowMapper, id);
        if (users.isEmpty()) {
            return Optional.empty();
        }
        User user = users.getFirst();
        user.setFriends(loadFriendIds(id));
        return Optional.of(user);
    }

    @Override
    public Collection<User> findAll() {
        return jdbcTemplate.query(
                "SELECT user_id, email, login, name, birthday FROM users ORDER BY user_id",
                userRowMapper);
    }

    @Override
    public void addFriend(long userId, long friendId) {
        jdbcTemplate.update("INSERT INTO friendships (user_id, friend_id) VALUES (?, ?)", userId, friendId);
        log.debug("Friendship added: userId={}, friendId={}", userId, friendId);
    }

    @Override
    public void removeFriend(long userId, long friendId) {
        jdbcTemplate.update("DELETE FROM friendships WHERE user_id = ? AND friend_id = ?", userId, friendId);
        log.debug("Friendship removed: userId={}, friendId={}", userId, friendId);
    }

    @Override
    public List<User> getFriends(long userId) {
        return jdbcTemplate.query(
                "SELECT u.user_id, u.email, u.login, u.name, u.birthday "
                        + "FROM users u "
                        + "JOIN friendships f ON u.user_id = f.friend_id "
                        + "WHERE f.user_id = ? "
                        + "ORDER BY u.user_id",
                userRowMapper, userId);
    }

    @Override
    public List<User> getCommonFriends(long userId, long otherId) {
        return jdbcTemplate.query(
                "SELECT u.user_id, u.email, u.login, u.name, u.birthday "
                        + "FROM users u "
                        + "JOIN friendships f1 ON u.user_id = f1.friend_id AND f1.user_id = ? "
                        + "JOIN friendships f2 ON u.user_id = f2.friend_id AND f2.user_id = ? "
                        + "ORDER BY u.user_id",
                userRowMapper, userId, otherId);
    }

    private Set<Long> loadFriendIds(long userId) {
        List<Long> friendIds = jdbcTemplate.query(
                "SELECT friend_id FROM friendships WHERE user_id = ?",
                (rs, rowNum) -> rs.getLong("friend_id"),
                userId);
        return new HashSet<>(friendIds);
    }
}
