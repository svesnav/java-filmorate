package ru.yandex.practicum.filmorate.storage.film;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import ru.yandex.practicum.filmorate.model.Director;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.model.Genre;
import ru.yandex.practicum.filmorate.model.Mpa;
import ru.yandex.practicum.filmorate.model.User;
import ru.yandex.practicum.filmorate.storage.director.DirectorDbStorage;
import ru.yandex.practicum.filmorate.storage.user.UserDbStorage;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@AutoConfigureTestDatabase
@Import({FilmDbStorage.class, UserDbStorage.class, DirectorDbStorage.class})
@RequiredArgsConstructor(onConstructor_ = @Autowired)
class FilmDbStorageTest {
    private final FilmDbStorage filmStorage;
    private final UserDbStorage userStorage;
    private final DirectorDbStorage directorStorage;

    private User createValidUser() {
        User user = new User();
        user.setEmail("film-user@mail.ru");
        user.setLogin("film-user");
        user.setName("Film User");
        user.setBirthday(LocalDate.of(1990, 1, 1));
        return user;
    }

    private Film createValidFilm() {
        Film film = new Film();
        film.setName("Test Film");
        film.setDescription("Test description");
        film.setReleaseDate(LocalDate.of(2000, 1, 1));
        film.setDuration(120);
        Mpa mpa = new Mpa();
        mpa.setId(1);
        film.setMpa(mpa);
        Genre genre = new Genre();
        genre.setId(1);
        Set<Genre> genres = new LinkedHashSet<>();
        genres.add(genre);
        film.setGenres(genres);
        return film;
    }

    @Test
    void testAddAndFindFilm() {
        Film created = filmStorage.add(createValidFilm());

        assertThat(created.getId()).isPositive();
        assertThat(filmStorage.findById(created.getId()))
                .isPresent()
                .hasValueSatisfying(film -> {
                    assertThat(film.getName()).isEqualTo("Test Film");
                    assertThat(film.getMpa().getName()).isEqualTo("G");
                    assertThat(film.getGenres()).hasSize(1);
                });
    }

    @Test
    void testUpdateFilm() {
        Film film = filmStorage.add(createValidFilm());
        film.setName("Updated Film");
        Genre genre = new Genre();
        genre.setId(2);
        Set<Genre> genres = new LinkedHashSet<>();
        genres.add(genre);
        film.setGenres(genres);

        Film updated = filmStorage.update(film);

        assertThat(updated.getName()).isEqualTo("Updated Film");
        assertThat(filmStorage.findById(film.getId()).orElseThrow().getGenres())
                .extracting(Genre::getId)
                .containsExactly(2);
    }

    @Test
    void testFindAllFilms() {
        filmStorage.add(createValidFilm());

        assertThat(filmStorage.findAll()).isNotEmpty();
    }

    @Test
    void testAddAndRemoveLike() {
        Film film = filmStorage.add(createValidFilm());
        long userId = userStorage.add(createValidUser()).getId();

        filmStorage.addLike(film.getId(), userId);

        assertThat(filmStorage.findById(film.getId()).orElseThrow().getLikes())
                .contains(userId);

        filmStorage.removeLike(film.getId(), userId);

        assertThat(filmStorage.findById(film.getId()).orElseThrow().getLikes())
                .doesNotContain(userId);
    }

    @Test
    void testGetPopular() {
        Film film1 = createValidFilm();
        film1.setName("Film 1");
        Film film2 = createValidFilm();
        film2.setName("Film 2");
        film1 = filmStorage.add(film1);
        film2 = filmStorage.add(film2);
        long userId1 = userStorage.add(createValidUser()).getId();
        ru.yandex.practicum.filmorate.model.User user2 = new ru.yandex.practicum.filmorate.model.User();
        user2.setEmail("popular@mail.ru");
        user2.setLogin("popular");
        user2.setName("Popular User");
        user2.setBirthday(LocalDate.of(1990, 1, 1));
        long userId2 = userStorage.add(user2).getId();
        filmStorage.addLike(film1.getId(), userId1);
        filmStorage.addLike(film2.getId(), userId1);
        filmStorage.addLike(film2.getId(), userId2);

        assertThat(filmStorage.getPopular(10, null, null))
                .first()
                .extracting(Film::getId)
                .isEqualTo(film2.getId());
    }

    @Test
    void testSearchByTitleAndDirectorSortedByPopularity() {
        Director otherDirector = new Director();
        otherDirector.setName("Энг Ли");
        otherDirector = directorStorage.add(otherDirector);
        Director matchingDirector = new Director();
        matchingDirector.setName("Иван Крадов");
        matchingDirector = directorStorage.add(matchingDirector);

        Film first = createValidFilm();
        first.setName("Крадущийся тигр");
        first.setDirectors(Set.of(otherDirector));
        first = filmStorage.add(first);
        Film second = createValidFilm();
        second.setName("Ночной дозор");
        second.setDirectors(Set.of(matchingDirector));
        second = filmStorage.add(second);
        Film third = createValidFilm();
        third.setName("Крадущийся в ночи");
        third.setDirectors(Set.of(matchingDirector));
        third = filmStorage.add(third);

        long firstUserId = userStorage.add(createValidUser()).getId();
        User secondUser = new User();
        secondUser.setEmail("search@mail.ru");
        secondUser.setLogin("search");
        secondUser.setName("Search User");
        secondUser.setBirthday(LocalDate.of(1990, 1, 1));
        long secondUserId = userStorage.add(secondUser).getId();
        filmStorage.addLike(first.getId(), firstUserId);
        filmStorage.addLike(second.getId(), firstUserId);
        filmStorage.addLike(second.getId(), secondUserId);

        assertThat(filmStorage.search("КРАД", Set.of("title")))
                .extracting(Film::getId)
                .containsExactly(first.getId(), third.getId());
        assertThat(filmStorage.search("КРАД", Set.of("director")))
                .extracting(Film::getId)
                .containsExactly(second.getId(), third.getId());
        assertThat(filmStorage.search("КРАД", Set.of("title", "director")))
                .extracting(Film::getId)
                .containsExactly(second.getId(), first.getId(), third.getId());
    }

    @Test
    void testDeleteFilm() {
        Film film = filmStorage.add(createValidFilm());

        filmStorage.delete(film.getId());

        assertThat(filmStorage.findById(film.getId())).isEmpty();
    }
}
