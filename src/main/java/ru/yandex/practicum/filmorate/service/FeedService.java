package ru.yandex.practicum.filmorate.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.model.FeedEvent;
import ru.yandex.practicum.filmorate.storage.feed.FeedStorage;
import ru.yandex.practicum.filmorate.storage.user.UserStorage;

import java.util.List;

@Slf4j
@Service
public class FeedService {
    private final FeedStorage feedStorage;
    private final UserStorage userStorage;

    public FeedService(@Qualifier("feedDbStorage") FeedStorage feedStorage,
                       @Qualifier("userDbStorage") UserStorage userStorage) {
        this.feedStorage = feedStorage;
        this.userStorage = userStorage;
    }

    public List<FeedEvent> getFeed(long userId) {
        userStorage.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id " + userId + " not found"));
        List<FeedEvent> events = feedStorage.getFeed(userId);
        log.info("Showed feed of user {}", userId);
        return events;
    }
}
