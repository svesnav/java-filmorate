package ru.yandex.practicum.filmorate.storage.feed;

import ru.yandex.practicum.filmorate.model.FeedEvent;

import java.util.List;

public interface FeedStorage {
    FeedEvent add(FeedEvent event);

    List<FeedEvent> getFeed(long userId);
}
