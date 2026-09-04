package ru.yandex.practicum.filmorate.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.exception.ValidationException;
import ru.yandex.practicum.filmorate.model.EventType;
import ru.yandex.practicum.filmorate.model.FeedEvent;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.model.Genre;
import ru.yandex.practicum.filmorate.model.Mpa;
import ru.yandex.practicum.filmorate.model.Operation;
import ru.yandex.practicum.filmorate.storage.feed.FeedStorage;
import ru.yandex.practicum.filmorate.storage.film.FilmStorage;
import ru.yandex.practicum.filmorate.storage.genre.GenreStorage;
import ru.yandex.practicum.filmorate.storage.mpa.MpaStorage;
import ru.yandex.practicum.filmorate.storage.user.UserStorage;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FilmService {
    private static final Set<String> SEARCH_FIELDS = Set.of("title", "director");

    private final FilmStorage filmStorage;
    private final UserStorage userStorage;
    private final MpaStorage mpaStorage;
    private final GenreStorage genreStorage;
    private final FeedStorage feedStorage;

    @Autowired
    public FilmService(@Qualifier("filmDbStorage") FilmStorage filmStorage,
                       @Qualifier("userDbStorage") UserStorage userStorage,
                       @Qualifier("mpaDbStorage") MpaStorage mpaStorage,
                       @Qualifier("genreDbStorage") GenreStorage genreStorage,
                       @Qualifier("feedDbStorage") FeedStorage feedStorage) {
        this.filmStorage = filmStorage;
        this.userStorage = userStorage;
        this.mpaStorage = mpaStorage;
        this.genreStorage = genreStorage;
        this.feedStorage = feedStorage;
    }

    public List<Film> findAll() {
        log.info("Showed all films");
        return filmStorage.findAll().stream().toList();
    }

    public Film findById(long id) {
        log.info("Showed film with id {}", id);
        return getFilmOrThrow(id);
    }

    public Film create(Film film) {
        validate(film);
        prepareFilm(film);
        Film created = filmStorage.add(film);
        log.info("Film created: {}", created);
        return created;
    }

    public Film update(Film film) {
        getFilmOrThrow(film.getId());
        validate(film);
        prepareFilm(film);
        Film updated = filmStorage.update(film);
        log.info("Film updated: {}", updated);
        return updated;
    }

    public void delete(long id) {
        getFilmOrThrow(id);
        filmStorage.delete(id);
        log.info("Film deleted: id={}", id);
    }

    public void addLike(long filmId, long userId) {
        getUserOrThrow(userId);
        getFilmOrThrow(filmId);
        filmStorage.addLike(filmId, userId);
        addFeedEvent(userId, Operation.ADD, filmId);
        log.info("User {} liked film {}", userId, filmId);
    }

    public void removeLike(long filmId, long userId) {
        getUserOrThrow(userId);
        getFilmOrThrow(filmId);
        filmStorage.removeLike(filmId, userId);
        addFeedEvent(userId, Operation.REMOVE, filmId);
        log.info("User {} removed like from film {}", userId, filmId);
    }

    public List<Film> getPopular(Integer count, Integer genreId, Integer year) {
        log.info("Showed {} popular films with genreId={} and year={}", count, genreId, year);
        return filmStorage.getPopular(count, genreId, year);
    }

    public List<Film> search(String query, Set<String> by) {
        if (query == null || query.isBlank()) {
            throw new ValidationException("Search query cannot be empty");
        }
        if (by == null || by.isEmpty()) {
            throw new ValidationException("Search fields cannot be empty");
        }
        Set<String> searchFields = by.stream()
                .map(field -> field.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (!SEARCH_FIELDS.containsAll(searchFields)) {
            throw new ValidationException("Invalid search field: " + by);
        }
        log.info("Searched films by {} with query {}", searchFields, query);
        return filmStorage.search(query, searchFields);
    }

    private Film getFilmOrThrow(long id) {
        return filmStorage.findById(id)
                .orElseThrow(() -> {
                    log.warn("Film not found: {}", id);
                    return new NotFoundException("Film with id " + id + " not found");
                });
    }

    private void addFeedEvent(long userId, Operation operation, long filmId) {
        feedStorage.add(new FeedEvent(System.currentTimeMillis(), userId, EventType.LIKE,
                operation, 0, filmId));
    }

    private void getUserOrThrow(long id) {
        userStorage.findById(id)
                .orElseThrow(() -> {
                    log.warn("User not found: {}", id);
                    return new NotFoundException("User with id " + id + " not found");
                });
    }

    private void prepareFilm(Film film) {
        if (film.getGenres() == null) {
            film.setGenres(new LinkedHashSet<>());
        }
        if (film.getLikes() == null) {
            film.setLikes(new HashSet<>());
        }
        if (film.getMpa() == null) {
            log.warn("Film mpa validation failed");
            throw new ValidationException("Mpa rating must be specified");
        }
        Mpa mpa = mpaStorage.findById(film.getMpa().getId())
                .orElseThrow(() -> {
                    log.warn("Mpa rating not found: {}", film.getMpa().getId());
                    return new NotFoundException("Mpa rating with id " + film.getMpa().getId() + " not found");
                });
        film.setMpa(mpa);
        List<Integer> genreIds = film.getGenres().stream()
                .map(Genre::getId)
                .toList();
        if (genreIds.isEmpty()) {
            return;
        }
        Map<Integer, Genre> genresById = genreStorage.findByIds(genreIds).stream()
                .collect(Collectors.toMap(Genre::getId, genre -> genre));
        if (genresById.size() < genreIds.size()) {
            log.warn("One or more genres not found: {}", genreIds);
            throw new NotFoundException("One or more genres not found");
        }
        Set<Genre> genres = new LinkedHashSet<>();
        for (Integer genreId : genreIds) {
            genres.add(genresById.get(genreId));
        }
        film.setGenres(genres);
    }

    private void validate(Film film) {
        if (film.getName() == null || film.getName().isBlank()) {
            log.warn("Film name validation failed");
            throw new ValidationException("Film name cannot be empty");
        }
        if (film.getDescription() != null && film.getDescription().length() > 200) {
            log.warn("Film description validation failed");
            throw new ValidationException("Film description must be 200 characters or less");
        }
        if (film.getReleaseDate() == null ||
                film.getReleaseDate().isBefore(LocalDate.of(1895, 12, 28))) {
            log.warn("Film releaseDate validation failed");
            throw new ValidationException("Release date must be after 1895-12-27");
        }
        if (film.getDuration() <= 0) {
            log.warn("Film duration validation failed");
            throw new ValidationException("Film duration must be positive");
        }
        if (film.getMpa() == null || film.getMpa().getId() <= 0) {
            log.warn("Film mpa validation failed");
            throw new ValidationException("Mpa rating must be specified");
        }
    }

    public List<Film> getFilmsByDirectorSorted(long directorId, String sortBy) {
        if (!sortBy.equals("year") && !sortBy.equals("likes")) {
            throw new ValidationException("Invalid sortBy value: " + sortBy);
        }
        return filmStorage.getFilmsByDirectorSorted(directorId, sortBy);
    }
}
