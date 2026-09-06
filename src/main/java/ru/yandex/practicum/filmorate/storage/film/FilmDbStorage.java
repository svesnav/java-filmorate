package ru.yandex.practicum.filmorate.storage.film;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.filmorate.exception.ValidationException;
import ru.yandex.practicum.filmorate.model.Director;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.model.Genre;
import ru.yandex.practicum.filmorate.model.Mpa;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Repository
@Qualifier("filmDbStorage")
@RequiredArgsConstructor
public class FilmDbStorage implements FilmStorage {
    private static final int DEFAULT_POPULAR_COUNT = 10;

    private final JdbcTemplate jdbcTemplate;

    private static final String SELECT_FILM_DIRECTORS =
            "SELECT fd.film_id, d.director_id, d.name FROM film_directors fd " +
                    "JOIN directors d ON fd.director_id = d.director_id WHERE fd.film_id IN (%s)";

    private static final String INSERT_FILM_DIRECTOR =
            "INSERT INTO film_directors (film_id, director_id) VALUES (?, ?)";

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
        saveDirectors(film);
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
        jdbcTemplate.update("DELETE FROM film_directors WHERE film_id = ?", film.getId());
        saveDirectors(film);
        log.debug("Film updated in database: {}", film);
        return findById(film.getId()).orElse(film);
    }

    @Override
    public void delete(long id) {
        jdbcTemplate.update("DELETE FROM review_likes WHERE review_id IN " +
                "(SELECT review_id FROM reviews WHERE film_id = ?)", id);
        jdbcTemplate.update("DELETE FROM reviews WHERE film_id = ?", id);
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
        enrichFilms(films);
        return Optional.of(films.getFirst());
    }

    @Override
    public Collection<Film> findAll() {
        List<Film> films = jdbcTemplate.query(
                "SELECT f.film_id, f.name, f.description, f.release_date, f.duration, "
                        + "f.mpa_id, m.name AS mpa_name "
                        + "FROM films f JOIN mpa m ON f.mpa_id = m.mpa_id ORDER BY f.film_id",
                filmRowMapper);
        enrichFilms(films);
        return films;
    }

    @Override
    public List<Film> getPopular(Integer count, Integer genreId, Integer year) {
        int limit = count == null ? DEFAULT_POPULAR_COUNT : count;
        StringBuilder sql = new StringBuilder(
                "SELECT f.film_id, f.name, f.description, f.release_date, f.duration, " +
                        "f.mpa_id, m.name AS mpa_name " +
                        "FROM films f " +
                        "JOIN mpa m ON f.mpa_id = m.mpa_id " +
                        "LEFT JOIN film_likes fl ON f.film_id = fl.film_id ");

        List<Object> params = new ArrayList<>();
        List<String> conditions = new ArrayList<>();

        if (genreId != null) {
            conditions.add("EXISTS (SELECT 1 FROM film_genres fg WHERE fg.film_id = f.film_id AND fg.genre_id = ?)");
            params.add(genreId);
        }
        if (year != null) {
            conditions.add("f.release_date >= ? AND f.release_date < ?");
            params.add(LocalDate.of(year, 1, 1));
            params.add(LocalDate.of(year + 1, 1, 1));
        }

        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }

        sql.append(" GROUP BY f.film_id, f.name, f.description, f.release_date, f.duration, f.mpa_id, m.name ");
        sql.append(" ORDER BY COUNT(fl.user_id) DESC, f.film_id ASC ");
        sql.append(" LIMIT ?");
        params.add(limit);

        List<Film> films = jdbcTemplate.query(sql.toString(), filmRowMapper, params.toArray());
        enrichFilms(films);
        return films;
    }

    @Override
    public List<Film> search(String query, Set<String> by) {
        List<String> conditions = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();
        String pattern = "%" + query.toLowerCase(Locale.ROOT) + "%";
        if (by.contains("title")) {
            conditions.add("LOWER(f.name) LIKE ?");
            parameters.add(pattern);
        }
        if (by.contains("director")) {
            conditions.add("EXISTS (SELECT 1 FROM film_directors fd " +
                    "JOIN directors d ON fd.director_id = d.director_id " +
                    "WHERE fd.film_id = f.film_id AND LOWER(d.name) LIKE ?)");
            parameters.add(pattern);
        }
        String sql = "SELECT f.film_id, f.name, f.description, f.release_date, f.duration, " +
                "f.mpa_id, m.name AS mpa_name " +
                "FROM films f " +
                "JOIN mpa m ON f.mpa_id = m.mpa_id " +
                "LEFT JOIN film_likes fl ON f.film_id = fl.film_id " +
                "WHERE " + String.join(" OR ", conditions) + " " +
                "GROUP BY f.film_id, f.name, f.description, f.release_date, f.duration, f.mpa_id, m.name " +
                "ORDER BY COUNT(fl.user_id) DESC, f.film_id ASC";
        List<Film> films = jdbcTemplate.query(sql, filmRowMapper, parameters.toArray());
        enrichFilms(films);
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

    @Override
    public Optional<Long> findMostSimilarUserId(long userId) {
        String sql = "SELECT fl2.user_id " +
                "FROM film_likes fl1 " +
                "JOIN film_likes fl2 ON fl1.film_id = fl2.film_id " +
                "WHERE fl1.user_id = ? AND fl2.user_id != ? " +
                "GROUP BY fl2.user_id " +
                "ORDER BY COUNT(fl2.film_id) DESC, fl2.user_id ASC " +
                "LIMIT 1";
        List<Long> ids = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("user_id"), userId, userId);
        return ids.stream().findFirst();
    }

    @Override
    public List<Film> getRecommendedFilms(long userId, long similarUserId) {
        String sql = "SELECT f.film_id, f.name, f.description, f.release_date, f.duration, " +
                "f.mpa_id, m.name AS mpa_name " +
                "FROM film_likes fl " +
                "JOIN films f ON fl.film_id = f.film_id " +
                "JOIN mpa m ON f.mpa_id = m.mpa_id " +
                "WHERE fl.user_id = ? " +
                "AND f.film_id NOT IN (SELECT film_id FROM film_likes WHERE user_id = ?) " +
                "ORDER BY f.film_id";
        List<Film> films = jdbcTemplate.query(sql, filmRowMapper, similarUserId, userId);
        enrichFilms(films);
        return films;
    }

    private void saveGenres(Film film) {
        if (film.getGenres() == null || film.getGenres().isEmpty()) {
            return;
        }
        List<Genre> genres = new ArrayList<>(film.getGenres());
        jdbcTemplate.batchUpdate(
                "INSERT INTO film_genres (film_id, genre_id) VALUES (?, ?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        ps.setLong(1, film.getId());
                        ps.setInt(2, genres.get(i).getId());
                    }

                    @Override
                    public int getBatchSize() {
                        return genres.size();
                    }
                });
    }

    private void saveDirectors(Film film) {
        if (film.getDirectors() == null || film.getDirectors().isEmpty()) {
            return;
        }
        List<Director> directors = new ArrayList<>(film.getDirectors());
        jdbcTemplate.batchUpdate(INSERT_FILM_DIRECTOR, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ps.setLong(1, film.getId());
                ps.setLong(2, directors.get(i).getId());
            }

            @Override
            public int getBatchSize() {
                return directors.size();
            }
        });
    }

    private void enrichFilms(List<Film> films) {
        if (films.isEmpty()) {
            return;
        }
        List<Long> filmIds = films.stream().map(Film::getId).toList();
        Map<Long, Set<Genre>> genresByFilmId = getGenresByFilmIds(filmIds);
        Map<Long, Set<Long>> likesByFilmId = getLikesByFilmIds(filmIds);
        Map<Long, Set<Director>> directorsByFilmId = getDirectorsByFilmIds(filmIds);
        films.forEach(film -> {
            film.setGenres(genresByFilmId.getOrDefault(film.getId(), new LinkedHashSet<>()));
            film.setLikes(likesByFilmId.getOrDefault(film.getId(), new HashSet<>()));
            film.setDirectors(directorsByFilmId.getOrDefault(film.getId(), new LinkedHashSet<>()));
        });
    }

    private Map<Long, Set<Genre>> getGenresByFilmIds(List<Long> filmIds) {
        String inSql = String.join(",", Collections.nCopies(filmIds.size(), "?"));
        String sql = "SELECT fg.film_id, g.genre_id, g.name "
                + "FROM film_genres fg "
                + "JOIN genres g ON fg.genre_id = g.genre_id "
                + "WHERE fg.film_id IN (" + inSql + ") "
                + "ORDER BY fg.film_id, g.genre_id";
        Map<Long, Set<Genre>> genresByFilmId = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            long filmId = rs.getLong("film_id");
            Genre genre = new Genre();
            genre.setId(rs.getInt("genre_id"));
            genre.setName(rs.getString("name"));
            genresByFilmId.computeIfAbsent(filmId, id -> new LinkedHashSet<>()).add(genre);
        }, filmIds.toArray());
        return genresByFilmId;
    }

    private Map<Long, Set<Long>> getLikesByFilmIds(List<Long> filmIds) {
        String inSql = String.join(",", Collections.nCopies(filmIds.size(), "?"));
        String sql = "SELECT film_id, user_id FROM film_likes WHERE film_id IN (" + inSql + ")";
        Map<Long, Set<Long>> likesByFilmId = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            long filmId = rs.getLong("film_id");
            likesByFilmId.computeIfAbsent(filmId, id -> new HashSet<>()).add(rs.getLong("user_id"));
        }, filmIds.toArray());
        return likesByFilmId;
    }

    private Map<Long, Set<Director>> getDirectorsByFilmIds(List<Long> filmIds) {
        if (filmIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String inSql = String.join(",", Collections.nCopies(filmIds.size(), "?"));
        String sql = String.format(SELECT_FILM_DIRECTORS, inSql);
        Map<Long, Set<Director>> directorsByFilmId = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            long filmId = rs.getLong("film_id");
            Director director = new Director();
            director.setId(rs.getLong("director_id"));
            director.setName(rs.getString("name"));
            directorsByFilmId.computeIfAbsent(filmId, id -> new LinkedHashSet<>()).add(director);
        }, filmIds.toArray());
        return directorsByFilmId;
    }

    @Override
    public List<Film> getFilmsByDirectorSorted(long directorId, String sortBy) {
        String orderBy = switch (sortBy) {
            case "year" -> "ORDER BY f.release_date ASC";
            case "likes" -> "ORDER BY like_count DESC";
            default -> throw new ValidationException("Invalid sortBy value: " + sortBy);
        };
        String sql = "SELECT f.film_id, f.name, f.description, f.release_date, f.duration, " +
                "f.mpa_id, m.name AS mpa_name, COUNT(fl.user_id) AS like_count " +
                "FROM films f " +
                "JOIN film_directors fd ON f.film_id = fd.film_id " +
                "JOIN directors d ON fd.director_id = d.director_id " +
                "LEFT JOIN mpa m ON f.mpa_id = m.mpa_id " +
                "LEFT JOIN film_likes fl ON f.film_id = fl.film_id " +
                "WHERE d.director_id = ? " +
                "GROUP BY f.film_id, f.name, f.description, f.release_date, f.duration, f.mpa_id, m.name " +
                orderBy;
        List<Film> films = jdbcTemplate.query(sql, filmRowMapper, directorId);
        enrichFilms(films);
        return films;
    }

    @Override
    public Collection<Film> getCommonFilms(long userId, long friendId) {
        String sql = "SELECT f.film_id, f.name, f.description, f.release_date, f.duration, f.mpa_id, " +
                "m.name AS mpa_name " +
                "FROM films f " +
                "JOIN mpa m ON f.mpa_id = m.mpa_id " +
                "JOIN film_likes fl1 ON f.film_id = fl1.film_id AND fl1.user_id = ? " +
                "JOIN film_likes fl2 ON f.film_id = fl2.film_id AND fl2.user_id = ? " +
                "LEFT JOIN film_likes fl3 ON f.film_id = fl3.film_id " +
                "GROUP BY f.film_id, f.name, f.description, f.release_date, f.duration, f.mpa_id, m.name " +
                "ORDER BY COUNT(fl3.user_id) DESC, f.film_id ASC";

        List<Film> commonFilms = jdbcTemplate.query(sql, filmRowMapper, userId, friendId);

        if (!commonFilms.isEmpty()) {
            enrichFilms(commonFilms);
        }

        return commonFilms;
    }
}