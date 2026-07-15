package ru.yandex.practicum.filmorate.storage.genre;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.filmorate.model.Genre;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@Qualifier("genreDbStorage")
@RequiredArgsConstructor
public class GenreDbStorage implements GenreStorage {
    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Genre> genreRowMapper = (rs, rowNum) -> {
        Genre genre = new Genre();
        genre.setId(rs.getInt("genre_id"));
        genre.setName(rs.getString("name"));
        return genre;
    };

    @Override
    public Collection<Genre> findAll() {
        return jdbcTemplate.query("SELECT genre_id, name FROM genres ORDER BY genre_id", genreRowMapper);
    }

    @Override
    public Optional<Genre> findById(int id) {
        List<Genre> genres = jdbcTemplate.query(
                "SELECT genre_id, name FROM genres WHERE genre_id = ?",
                genreRowMapper, id);
        return genres.stream().findFirst();
    }
}
