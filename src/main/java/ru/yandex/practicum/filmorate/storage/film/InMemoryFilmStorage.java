package ru.yandex.practicum.filmorate.storage.film;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.filmorate.model.Film;

import java.util.*;

@Slf4j
@Component
public class InMemoryFilmStorage implements FilmStorage {
    private final Map<Long, Film> films = new HashMap<>();
    private int idCounter = 1;
    private static final int DEFAULT_POPULAR_COUNT = 10;



    @Override
    public Film add(Film film) {
        film.setId(idCounter++);
        films.put(film.getId(), film);
        log.debug("Film added to storage: {}", film);
        return film;
    }

    @Override
    public Film update(Film film) {
        films.put(film.getId(), film);
        log.debug("Film updated in storage: {}", film);
        return film;
    }

    @Override
    public void delete(long id) {
        films.remove(id);
        log.debug("Film deleted from storage: id={}", id);
    }

    @Override
    public Optional<Film> findById(long id) {
        return Optional.ofNullable(films.get(id));
    }

    @Override
    public Collection<Film> findAll() {
        return films.values();
    }

    public static List<Film> getPopular(Integer count, FilmStorage filmStorage) {
        int limit = count == null ? DEFAULT_POPULAR_COUNT : count;
        log.debug("Showed {} popular films", limit);
        return filmStorage.findAll().stream()
                .sorted(Comparator.comparingInt((Film film) -> film.getLikes().size()).reversed()
                        .thenComparingLong(Film::getId))
                .limit(limit)
                .toList();
    }
}
