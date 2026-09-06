package ru.yandex.practicum.filmorate.storage.user;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import ru.yandex.practicum.filmorate.model.User;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@AutoConfigureTestDatabase
@Import(UserDbStorage.class)
@RequiredArgsConstructor(onConstructor_ = @Autowired)
class UserDbStorageTest {
    private final UserDbStorage userStorage;

    private User createValidUser() {
        User user = new User();
        user.setEmail("test@mail.ru");
        user.setLogin("testlogin");
        user.setName("Test User");
        user.setBirthday(LocalDate.of(1990, 1, 1));
        return user;
    }

    @Test
    void testFindUserById() {
        User created = userStorage.add(createValidUser());
        Optional<User> userOptional = userStorage.findById(created.getId());

        assertThat(userOptional)
                .isPresent()
                .hasValueSatisfying(user ->
                        assertThat(user).hasFieldOrPropertyWithValue("id", created.getId())
                );
    }

    @Test
    void testAddAndFindUser() {
        User user = new User();
        user.setEmail("new@mail.ru");
        user.setLogin("newlogin");
        user.setName("New User");
        user.setBirthday(LocalDate.of(2000, 1, 1));

        User created = userStorage.add(user);

        assertThat(created.getId()).isPositive();
        assertThat(userStorage.findById(created.getId()))
                .isPresent()
                .hasValueSatisfying(found -> {
                    assertThat(found.getEmail()).isEqualTo("new@mail.ru");
                    assertThat(found.getLogin()).isEqualTo("newlogin");
                });
    }

    @Test
    void testUpdateUser() {
        User user = userStorage.add(createValidUser());
        user.setName("Updated Name");

        User updated = userStorage.update(user);

        assertThat(updated.getName()).isEqualTo("Updated Name");
        assertThat(userStorage.findById(user.getId()).orElseThrow().getName()).isEqualTo("Updated Name");
    }

    @Test
    void testFindAllUsers() {
        User created = userStorage.add(createValidUser());

        assertThat(userStorage.findAll())
                .extracting(User::getId)
                .containsExactly(created.getId());
    }

    @Test
    void testAddAndRemoveFriend() {
        User user1 = new User();
        user1.setEmail("friend1@mail.ru");
        user1.setLogin("friend1");
        user1.setName("Friend 1");
        user1.setBirthday(LocalDate.of(1990, 1, 1));
        User user2 = new User();
        user2.setEmail("friend2@mail.ru");
        user2.setLogin("friend2");
        user2.setName("Friend 2");
        user2.setBirthday(LocalDate.of(1991, 1, 1));
        userStorage.add(user1);
        userStorage.add(user2);

        userStorage.addFriend(user1.getId(), user2.getId());

        assertThat(userStorage.getFriends(user1.getId()))
                .extracting(User::getId)
                .contains(user2.getId());
        assertThat(userStorage.getFriends(user2.getId()))
                .isEmpty();

        userStorage.removeFriend(user1.getId(), user2.getId());

        assertThat(userStorage.getFriends(user1.getId()))
                .isEmpty();
    }

    @Test
    void testGetCommonFriends() {
        User user1 = new User();
        user1.setEmail("common1@mail.ru");
        user1.setLogin("common1");
        user1.setName("Common 1");
        user1.setBirthday(LocalDate.of(1990, 1, 1));
        User user2 = new User();
        user2.setEmail("common2@mail.ru");
        user2.setLogin("common2");
        user2.setName("Common 2");
        user2.setBirthday(LocalDate.of(1991, 1, 1));
        User user3 = new User();
        user3.setEmail("common3@mail.ru");
        user3.setLogin("common3");
        user3.setName("Common 3");
        user3.setBirthday(LocalDate.of(1992, 1, 1));
        userStorage.add(user1);
        userStorage.add(user2);
        userStorage.add(user3);
        userStorage.addFriend(user1.getId(), user3.getId());
        userStorage.addFriend(user2.getId(), user3.getId());

        assertThat(userStorage.getCommonFriends(user1.getId(), user2.getId()))
                .extracting(User::getId)
                .containsExactly(user3.getId());
    }

    @Test
    void testDeleteUser() {
        User user = new User();
        user.setEmail("delete@mail.ru");
        user.setLogin("delete");
        user.setName("Delete");
        user.setBirthday(LocalDate.of(1990, 1, 1));
        User created = userStorage.add(user);

        userStorage.delete(created.getId());

        assertThat(userStorage.findById(created.getId())).isEmpty();
    }
}
