package ru.yandex.practicum.filmorate.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.exception.ValidationException;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.storage.film.FilmStorage;
import ru.yandex.practicum.filmorate.storage.film.InMemoryFilmStorage;
import ru.yandex.practicum.filmorate.storage.user.UserStorage;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

@Slf4j
@Service
public class FilmService {
    private static final int DEFAULT_POPULAR_COUNT = 10;

    private final FilmStorage filmStorage;
    private final UserStorage userStorage;

    @Autowired
    public FilmService(FilmStorage filmStorage, UserStorage userStorage) {
        this.filmStorage = filmStorage;
        this.userStorage = userStorage;
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
        if (film.getLikes() == null) {
            film.setLikes(new HashSet<>());
        }
        Film created = filmStorage.add(film);
        log.info("Film created: {}", created);
        return created;
    }

    public Film update(Film film) {
        Film existing = getFilmOrThrow(film.getId());
        validate(film);
        film.setLikes(existing.getLikes());
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
        Film film = getFilmOrThrow(filmId);
        film.getLikes().add(userId);
        log.info("User {} liked film {}", userId, filmId);
    }

    public void removeLike(long filmId, long userId) {
        getUserOrThrow(userId);
        Film film = getFilmOrThrow(filmId);
        film.getLikes().remove(userId);
        log.info("User {} removed like from film {}", userId, filmId);
    }

    public List<Film> getPopular(Integer count) {
        log.info("Showed {} popular films", count);
        return InMemoryFilmStorage.getPopular(count, filmStorage);
    }

    private Film getFilmOrThrow(long id) {
        return filmStorage.findById(id)
                .orElseThrow(() -> {
                    log.warn("Film not found: {}", id);
                    return new NotFoundException("Film with id " + id + " not found");
                });
    }

    private void getUserOrThrow(long id) {
        userStorage.findById(id)
                .orElseThrow(() -> {
                    log.warn("User not found: {}", id);
                    return new NotFoundException("User with id " + id + " not found");
                });
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
    }
}
