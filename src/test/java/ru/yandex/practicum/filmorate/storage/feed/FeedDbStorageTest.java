package ru.yandex.practicum.filmorate.storage.feed;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import ru.yandex.practicum.filmorate.model.EventType;
import ru.yandex.practicum.filmorate.model.FeedEvent;
import ru.yandex.practicum.filmorate.model.Operation;
import ru.yandex.practicum.filmorate.model.User;
import ru.yandex.practicum.filmorate.storage.user.UserDbStorage;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@AutoConfigureTestDatabase
@Import({FeedDbStorage.class, UserDbStorage.class})
@RequiredArgsConstructor(onConstructor_ = @Autowired)
class FeedDbStorageTest {
    private final FeedDbStorage feedStorage;
    private final UserDbStorage userStorage;

    private User createUser(String login) {
        User user = new User();
        user.setEmail(login + "@mail.ru");
        user.setLogin(login);
        user.setName(login);
        user.setBirthday(LocalDate.of(1990, 1, 1));
        return userStorage.add(user);
    }

    @Test
    void shouldSaveEventsAndReturnThemInExecutionOrder() {
        long userId = createUser("feed-user").getId();
        long otherUserId = createUser("other-user").getId();
        FeedEvent friendEvent = feedStorage.add(new FeedEvent(
                1000,
                userId,
                EventType.FRIEND,
                Operation.ADD,
                0,
                10
        ));
        FeedEvent likeEvent = feedStorage.add(new FeedEvent(
                2000,
                userId,
                EventType.LIKE,
                Operation.REMOVE,
                0,
                20
        ));

        assertThat(friendEvent.getEventId()).isPositive();
        assertThat(feedStorage.getFeed(userId))
                .extracting(FeedEvent::getEventId)
                .containsExactly(friendEvent.getEventId(), likeEvent.getEventId());
        assertThat(feedStorage.getFeed(userId).get(1))
                .returns(EventType.LIKE, FeedEvent::getEventType)
                .returns(Operation.REMOVE, FeedEvent::getOperation)
                .returns(20L, FeedEvent::getEntityId);
        assertThat(feedStorage.getFeed(otherUserId)).isEmpty();
    }
}
