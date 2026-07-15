package ru.yandex.practicum.filmorate.storage.film;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.model.Genre;
import ru.yandex.practicum.filmorate.model.Mpa;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Repository
@Qualifier("filmDbStorage")
@RequiredArgsConstructor
public class FilmDbStorage implements FilmStorage {
    private static final int DEFAULT_POPULAR_COUNT = 10;

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Film> filmRowMapper = (rs, rowNum) -> {
        Film film = new Film();
        film.setId(rs.getLong("film_id"));
        film.setName(rs.getString("name"));
        film.setDescription(rs.getString("description"));
        Date releaseDate = rs.getDate("release_date");
        if (releaseDate != null) {
            film.setReleaseDate(releaseDate.toLocalDate());
        }
        film.setDuration(rs.getInt("duration"));
        Mpa mpa = new Mpa();
        mpa.setId(rs.getInt("mpa_id"));
        mpa.setName(rs.getString("mpa_name"));
        film.setMpa(mpa);
        return film;
    };

    @Override
    public Film add(Film film) {
        String sql = "INSERT INTO films (name, description, release_date, duration, mpa_id) VALUES (?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, film.getName());
            ps.setString(2, film.getDescription());
            ps.setDate(3, Date.valueOf(film.getReleaseDate()));
            ps.setInt(4, film.getDuration());
            ps.setInt(5, film.getMpa().getId());
            return ps;
        }, keyHolder);
        film.setId(keyHolder.getKey().longValue());
        saveGenres(film);
        film.setLikes(new HashSet<>());
        log.debug("Film added to database: {}", film);
        return findById(film.getId()).orElse(film);
    }

    @Override
    public Film update(Film film) {
        String sql = "UPDATE films SET name = ?, description = ?, release_date = ?, duration = ?, mpa_id = ? "
                + "WHERE film_id = ?";
        jdbcTemplate.update(sql, film.getName(), film.getDescription(), Date.valueOf(film.getReleaseDate()),
                film.getDuration(), film.getMpa().getId(), film.getId());
        jdbcTemplate.update("DELETE FROM film_genres WHERE film_id = ?", film.getId());
        saveGenres(film);
        log.debug("Film updated in database: {}", film);
        return findById(film.getId()).orElse(film);
    }

    @Override
    public void delete(long id) {
        jdbcTemplate.update("DELETE FROM film_genres WHERE film_id = ?", id);
        jdbcTemplate.update("DELETE FROM film_likes WHERE film_id = ?", id);
        jdbcTemplate.update("DELETE FROM films WHERE film_id = ?", id);
        log.debug("Film deleted from database: id={}", id);
    }

    @Override
    public Optional<Film> findById(long id) {
        List<Film> films = jdbcTemplate.query(
                "SELECT f.film_id, f.name, f.description, f.release_date, f.duration, "
                        + "f.mpa_id, m.name AS mpa_name "
                        + "FROM films f JOIN mpa m ON f.mpa_id = m.mpa_id WHERE f.film_id = ?",
                filmRowMapper, id);
        if (films.isEmpty()) {
            return Optional.empty();
        }
        Film film = films.getFirst();
        enrichFilm(film);
        return Optional.of(film);
    }

    @Override
    public Collection<Film> findAll() {
        List<Film> films = jdbcTemplate.query(
                "SELECT f.film_id, f.name, f.description, f.release_date, f.duration, "
                        + "f.mpa_id, m.name AS mpa_name "
                        + "FROM films f JOIN mpa m ON f.mpa_id = m.mpa_id ORDER BY f.film_id",
                filmRowMapper);
        films.forEach(this::enrichFilm);
        return films;
    }

    @Override
    public List<Film> getPopular(Integer count) {
        int limit = count == null ? DEFAULT_POPULAR_COUNT : count;
        List<Film> films = jdbcTemplate.query(
                "SELECT f.film_id, f.name, f.description, f.release_date, f.duration, "
                        + "f.mpa_id, m.name AS mpa_name "
                        + "FROM films f "
                        + "JOIN mpa m ON f.mpa_id = m.mpa_id "
                        + "LEFT JOIN film_likes fl ON f.film_id = fl.film_id "
                        + "GROUP BY f.film_id, f.name, f.description, f.release_date, f.duration, f.mpa_id, m.name "
                        + "ORDER BY COUNT(fl.user_id) DESC, f.film_id ASC "
                        + "LIMIT ?",
                filmRowMapper, limit);
        films.forEach(this::enrichFilm);
        return films;
    }

    @Override
    public void addLike(long filmId, long userId) {
        jdbcTemplate.update("INSERT INTO film_likes (film_id, user_id) VALUES (?, ?)", filmId, userId);
        log.debug("Like added: filmId={}, userId={}", filmId, userId);
    }

    @Override
    public void removeLike(long filmId, long userId) {
        jdbcTemplate.update("DELETE FROM film_likes WHERE film_id = ? AND user_id = ?", filmId, userId);
        log.debug("Like removed: filmId={}, userId={}", filmId, userId);
    }

    private void saveGenres(Film film) {
        if (film.getGenres() == null) {
            return;
        }
        for (Genre genre : film.getGenres()) {
            jdbcTemplate.update("INSERT INTO film_genres (film_id, genre_id) VALUES (?, ?)",
                    film.getId(), genre.getId());
        }
    }

    private void enrichFilm(Film film) {
        film.setGenres(getGenres(film.getId()));
        film.setLikes(getLikes(film.getId()));
    }

    private Set<Genre> getGenres(long filmId) {
        List<Genre> genres = jdbcTemplate.query(
                "SELECT g.genre_id, g.name FROM genres g "
                        + "JOIN film_genres fg ON g.genre_id = fg.genre_id "
                        + "WHERE fg.film_id = ? ORDER BY g.genre_id",
                (rs, rowNum) -> {
                    Genre genre = new Genre();
                    genre.setId(rs.getInt("genre_id"));
                    genre.setName(rs.getString("name"));
                    return genre;
                },
                filmId);
        return new LinkedHashSet<>(genres);
    }

    private Set<Long> getLikes(long filmId) {
        List<Long> likes = jdbcTemplate.query(
                "SELECT user_id FROM film_likes WHERE film_id = ?",
                (rs, rowNum) -> rs.getLong("user_id"),
                filmId);
        return new HashSet<>(likes);
    }
}
