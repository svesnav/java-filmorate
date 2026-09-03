package ru.yandex.practicum.filmorate.storage.director;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.model.Director;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Collection;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class DirectorDbStorage implements DirectorStorage {
    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Director> directorRowMapper = (rs, rowNum) -> {
        Director director = new Director();
        director.setId(rs.getLong("director_id"));
        director.setName(rs.getString("name"));
        return director;
    };

    @Override
    public Director add(Director director) {
        String sql = "INSERT INTO directors (name) VALUES (?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, director.getName());
            return ps;
        }, keyHolder);
        director.setId(keyHolder.getKey().longValue());
        return director;
    }

    @Override
    public Director update(Director director) {
        String sql = "UPDATE directors SET name = ? WHERE director_id = ?";
        int rows = jdbcTemplate.update(sql, director.getName(), director.getId());
        if (rows == 0) {
            throw new NotFoundException("Director with id " + director.getId() + " not found");
        }
        return findById(director.getId()).orElse(director);
    }

    @Override
    public void delete(long id) {
        jdbcTemplate.update("DELETE FROM film_directors WHERE director_id = ?", id);
        jdbcTemplate.update("DELETE FROM directors WHERE director_id = ?", id);
    }

    @Override
    public Optional<Director> findById(long id) {
        return jdbcTemplate.query(
                "SELECT director_id, name FROM directors WHERE director_id = ?",
                directorRowMapper, id).stream().findFirst();
    }

    @Override
    public Collection<Director> findAll() {
        return jdbcTemplate.query("SELECT director_id, name FROM directors ORDER BY director_id",
                directorRowMapper);
    }
}