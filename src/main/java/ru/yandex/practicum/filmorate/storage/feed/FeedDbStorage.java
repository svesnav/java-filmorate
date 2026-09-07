package ru.yandex.practicum.filmorate.storage.feed;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.filmorate.model.EventType;
import ru.yandex.practicum.filmorate.model.FeedEvent;
import ru.yandex.practicum.filmorate.model.Operation;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@Repository
@Qualifier("feedDbStorage")
@RequiredArgsConstructor
public class FeedDbStorage implements FeedStorage {
    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<FeedEvent> eventRowMapper = (rs, rowNum) -> new FeedEvent(
            rs.getLong("event_timestamp"),
            rs.getLong("user_id"),
            EventType.valueOf(rs.getString("event_type")),
            Operation.valueOf(rs.getString("operation")),
            rs.getLong("event_id"),
            rs.getLong("entity_id")
    );

    @Override
    public FeedEvent add(FeedEvent event) {
        String sql = "INSERT INTO feed_events (event_timestamp, user_id, event_type, operation, entity_id) "
                + "VALUES (?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, event.getTimestamp());
            ps.setLong(2, event.getUserId());
            ps.setString(3, event.getEventType().name());
            ps.setString(4, event.getOperation().name());
            ps.setLong(5, event.getEntityId());
            return ps;
        }, keyHolder);
        event.setEventId(keyHolder.getKey().longValue());
        return event;
    }

    @Override
    public List<FeedEvent> getFeed(long userId) {
        return jdbcTemplate.query(
                "SELECT event_id, event_timestamp, user_id, event_type, operation, entity_id "
                        + "FROM feed_events WHERE user_id = ? ORDER BY event_id",
                eventRowMapper,
                userId
        );
    }
}
