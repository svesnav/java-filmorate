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

    @Override
    public List<Film> getPopular(Integer count, Integer genreId, Integer year) {
        int limit = count == null ? DEFAULT_POPULAR_COUNT : count;
        return films.values().stream()
                .filter(film -> (genreId == null || film.getGenres().stream()
                        .anyMatch(genre -> genre.getId() == genreId)))
                .filter(film -> (year == null || film.getReleaseDate().getYear() == year))
                .sorted(Comparator.comparingInt((Film film) -> film.getLikes().size()).reversed()
                        .thenComparingLong(Film::getId))
                .limit(limit)
                .toList();
    }

    @Override
    public List<Film> search(String query, Set<String> by) {
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        return films.values().stream()
                .filter(film -> (by.contains("title") &&
                        film.getName().toLowerCase(Locale.ROOT).contains(normalizedQuery) ||
                        by.contains("director") && film.getDirectors() != null && film.getDirectors().stream()
                                .anyMatch(director -> director.getName().toLowerCase(Locale.ROOT)
                                        .contains(normalizedQuery))))
                .sorted(Comparator.comparingInt((Film film) -> film.getLikes().size()).reversed()
                        .thenComparingLong(Film::getId))
                .toList();
    }

    @Override
    public void addLike(long filmId, long userId) {
        films.get(filmId).getLikes().add(userId);
    }

    @Override
    public void removeLike(long filmId, long userId) {
        films.get(filmId).getLikes().remove(userId);
    }

    @Override
    public Optional<Long> findMostSimilarUserId(long userId) {
        Set<Long> userLikes = new HashSet<>();
        for (Film f : films.values()) {
            if (f.getLikes().contains(userId)) {
                userLikes.add(f.getId());
            }
        }

        if (userLikes.isEmpty()) {
            return Optional.empty();
        }

        Map<Long, Integer> intersections = new HashMap<>();
        for (Film f : films.values()) {
            if (userLikes.contains(f.getId())) {
                for (Long likerId : f.getLikes()) {
                    if (likerId != userId) {
                        intersections.put(likerId, intersections.getOrDefault(likerId, 0) + 1);
                    }
                }
            }
        }

        long mostSimilarId = -1;
        int maxIntersection = 0;
        for (Map.Entry<Long, Integer> entry : intersections.entrySet()) {
            if (entry.getValue() > maxIntersection ||
                    (entry.getValue() == maxIntersection && (mostSimilarId == -1 || entry.getKey() < mostSimilarId))) {
                maxIntersection = entry.getValue();
                mostSimilarId = entry.getKey();
            }
        }

        return mostSimilarId == -1 ? Optional.empty() : Optional.of(mostSimilarId);
    }

    @Override
    public List<Film> getRecommendedFilms(long userId, long similarUserId) {
        Set<Long> userLikes = new HashSet<>();
        for (Film f : films.values()) {
            if (f.getLikes().contains(userId)) {
                userLikes.add(f.getId());
            }
        }

        return films.values().stream()
                .filter(f -> f.getLikes().contains(similarUserId) && !userLikes.contains(f.getId()))
                .sorted(Comparator.comparingLong(Film::getId))
                .toList();
    }

    @Override
    public List<Film> getFilmsByDirectorSorted(long directorId, String sortBy) {
        return films.values().stream()
                .filter(f -> f.getDirectors() != null &&
                        f.getDirectors().stream().anyMatch(d -> d.getId() == directorId))
                .sorted((f1, f2) -> {
                    if ("year".equals(sortBy)) {
                        return f1.getReleaseDate().compareTo(f2.getReleaseDate());
                    } else {
                        return Integer.compare(f2.getLikes().size(), f1.getLikes().size());
                    }
                })
                .toList();
    }

    @Override
    public Collection<Film> getCommonFilms(long userId, long friendId) {
        return films.values().stream().filter(film -> film.getLikes() != null && film.getLikes().contains(userId)
                && film.getLikes().contains(friendId)).sorted(Comparator.comparingInt((Film film) -> film.getLikes()
                .size()).reversed().thenComparingLong(Film::getId)).toList();
    }
}
