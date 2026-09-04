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

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@AutoConfigureTestDatabase
@Import(FeedDbStorage.class)
@RequiredArgsConstructor(onConstructor_ = @Autowired)
class FeedDbStorageTest {
    private final FeedDbStorage feedStorage;

    @Test
    void shouldSaveEventsAndReturnThemInExecutionOrder() {
        FeedEvent friendEvent = feedStorage.add(new FeedEvent(
                1000,
                1,
                EventType.FRIEND,
                Operation.ADD,
                0,
                10
        ));
        FeedEvent likeEvent = feedStorage.add(new FeedEvent(
                2000,
                1,
                EventType.LIKE,
                Operation.REMOVE,
                0,
                20
        ));

        assertThat(friendEvent.getEventId()).isPositive();
        assertThat(feedStorage.getFeed(1))
                .extracting(FeedEvent::getEventId)
                .containsExactly(friendEvent.getEventId(), likeEvent.getEventId());
        assertThat(feedStorage.getFeed(1).get(1))
                .returns(EventType.LIKE, FeedEvent::getEventType)
                .returns(Operation.REMOVE, FeedEvent::getOperation)
                .returns(20L, FeedEvent::getEntityId);
        assertThat(feedStorage.getFeed(2)).isEmpty();
    }
}
