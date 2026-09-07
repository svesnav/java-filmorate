package ru.yandex.practicum.filmorate.storage;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.yandex.practicum.filmorate.model.Director;
import ru.yandex.practicum.filmorate.model.EventType;
import ru.yandex.practicum.filmorate.model.FeedEvent;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.model.Genre;
import ru.yandex.practicum.filmorate.model.Mpa;
import ru.yandex.practicum.filmorate.model.Operation;
import ru.yandex.practicum.filmorate.model.Review;
import ru.yandex.practicum.filmorate.model.User;
import ru.yandex.practicum.filmorate.storage.director.DirectorDbStorage;
import ru.yandex.practicum.filmorate.storage.feed.FeedDbStorage;
import ru.yandex.practicum.filmorate.storage.film.FilmDbStorage;
import ru.yandex.practicum.filmorate.storage.review.ReviewDbStorage;
import ru.yandex.practicum.filmorate.storage.user.UserDbStorage;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@AutoConfigureTestDatabase
@Import({FilmDbStorage.class, UserDbStorage.class, DirectorDbStorage.class,
        ReviewDbStorage.class, FeedDbStorage.class})
@RequiredArgsConstructor(onConstructor_ = @Autowired)
class CascadeDeleteStorageTest {
    private final FilmDbStorage filmStorage;
    private final UserDbStorage userStorage;
    private final DirectorDbStorage directorStorage;
    private final ReviewDbStorage reviewStorage;
    private final FeedDbStorage feedStorage;
    private final JdbcTemplate jdbcTemplate;

    private User user;
    private User otherUser;
    private User friend;
    private Film film;
    private Film otherFilm;
    private Director director;
    private Director otherDirector;
    private Review review;
    private Review otherReview;
    private Review secondReview;

    @BeforeEach
    void setUp() {
        user = createUser("user");
        otherUser = createUser("other");
        friend = createUser("friend");
        director = createDirector("Director");
        otherDirector = createDirector("Other director");
        film = createFilm("Film", Set.of(director, otherDirector));
        otherFilm = createFilm("Other film", Set.of(otherDirector));
        userStorage.addFriend(user.getId(), otherUser.getId());
        userStorage.addFriend(otherUser.getId(), user.getId());
        userStorage.addFriend(otherUser.getId(), friend.getId());
        filmStorage.addLike(film.getId(), user.getId());
        filmStorage.addLike(film.getId(), otherUser.getId());
        filmStorage.addLike(otherFilm.getId(), user.getId());
        filmStorage.addLike(otherFilm.getId(), otherUser.getId());
        review = createReview(user.getId(), film.getId());
        otherReview = createReview(otherUser.getId(), otherFilm.getId());
        secondReview = createReview(user.getId(), otherFilm.getId());
        reviewStorage.addLikeOrDislike(review.getReviewId(), user.getId(), true);
        reviewStorage.addLikeOrDislike(review.getReviewId(), otherUser.getId(), false);
        reviewStorage.addLikeOrDislike(otherReview.getReviewId(), user.getId(), true);
        reviewStorage.addLikeOrDislike(otherReview.getReviewId(), otherUser.getId(), true);
        reviewStorage.addLikeOrDislike(secondReview.getReviewId(), otherUser.getId(), true);
        feedStorage.add(new FeedEvent(1000, user.getId(), EventType.LIKE, Operation.ADD, 0, film.getId()));
        feedStorage.add(new FeedEvent(2000, otherUser.getId(), EventType.LIKE, Operation.ADD, 0, film.getId()));
    }

    @Test
    void shouldDeleteFilmWithDependentRecords() {
        filmStorage.delete(film.getId());

        assertThat(filmStorage.findById(film.getId())).isEmpty();
        assertThat(filmStorage.findById(otherFilm.getId())).isPresent();
        assertThat(reviewStorage.findById(review.getReviewId())).isEmpty();
        assertThat(reviewStorage.findById(otherReview.getReviewId())).isPresent();
        assertThat(reviewStorage.findById(secondReview.getReviewId())).isPresent();
        assertRowCount("film_genres", 1);
        assertRowCount("film_directors", 1);
        assertRowCount("film_likes", 2);
        assertRowCount("reviews", 2);
        assertRowCount("review_likes", 3);
        assertRowCount("users", 3);
        assertRowCount("directors", 2);
        assertRowCount("feed_events", 2);
    }

    @Test
    void shouldDeleteUserWithDependentRecords() {
        userStorage.delete(user.getId());

        assertThat(userStorage.findById(user.getId())).isEmpty();
        assertThat(userStorage.findById(otherUser.getId())).isPresent();
        assertThat(userStorage.getFriends(otherUser.getId()))
                .extracting(User::getId)
                .containsExactly(friend.getId());
        assertThat(reviewStorage.findById(review.getReviewId())).isEmpty();
        assertThat(reviewStorage.findById(secondReview.getReviewId())).isEmpty();
        assertThat(reviewStorage.findById(otherReview.getReviewId())).isPresent()
                .hasValueSatisfying(found -> assertThat(found.getUseful()).isEqualTo(1));
        assertThat(filmStorage.findById(film.getId())).isPresent()
                .hasValueSatisfying(found -> assertThat(found.getLikes()).containsExactly(otherUser.getId()));
        assertThat(feedStorage.getFeed(user.getId())).isEmpty();
        assertThat(feedStorage.getFeed(otherUser.getId())).hasSize(1);
        assertRowCount("users", 2);
        assertRowCount("friendships", 1);
        assertRowCount("film_likes", 2);
        assertRowCount("reviews", 1);
        assertRowCount("review_likes", 1);
        assertRowCount("films", 2);
        assertRowCount("film_genres", 2);
        assertRowCount("film_directors", 3);
    }

    @Test
    void shouldDeleteDirectorWithoutDeletingFilms() {
        directorStorage.delete(director.getId());

        assertThat(directorStorage.findById(director.getId())).isEmpty();
        assertThat(directorStorage.findById(otherDirector.getId())).isPresent();
        assertThat(filmStorage.findById(film.getId())).isPresent()
                .hasValueSatisfying(found -> assertThat(found.getDirectors())
                        .extracting(Director::getId).containsExactly(otherDirector.getId()));
        assertThat(filmStorage.findById(otherFilm.getId())).isPresent();
        assertRowCount("film_directors", 2);
        assertRowCount("films", 2);
        assertRowCount("reviews", 3);
        assertRowCount("review_likes", 5);
    }

    @Test
    void shouldDeleteReviewWithRatings() {
        reviewStorage.delete(review.getReviewId());

        assertThat(reviewStorage.findById(review.getReviewId())).isEmpty();
        assertThat(reviewStorage.findById(otherReview.getReviewId())).isPresent();
        assertThat(reviewStorage.findById(secondReview.getReviewId())).isPresent();
        assertRowCount("reviews", 2);
        assertRowCount("review_likes", 3);
        assertRowCount("users", 3);
        assertRowCount("films", 2);
    }

    private User createUser(String login) {
        User created = new User();
        created.setEmail(login + "@mail.ru");
        created.setLogin(login);
        created.setBirthday(LocalDate.of(1990, 1, 1));
        return userStorage.add(created);
    }

    private Director createDirector(String name) {
        Director created = new Director();
        created.setName(name);
        return directorStorage.add(created);
    }

    private Film createFilm(String name, Set<Director> directors) {
        Film created = new Film();
        created.setName(name);
        created.setReleaseDate(LocalDate.of(2000, 1, 1));
        created.setDuration(120);
        Mpa mpa = new Mpa();
        mpa.setId(1);
        created.setMpa(mpa);
        Genre genre = new Genre();
        genre.setId(1);
        created.setGenres(Set.of(genre));
        created.setDirectors(directors);
        return filmStorage.add(created);
    }

    private Review createReview(long userId, long filmId) {
        Review created = new Review();
        created.setContent("Review");
        created.setIsPositive(true);
        created.setUserId(userId);
        created.setFilmId(filmId);
        return reviewStorage.create(created);
    }

    private void assertRowCount(String table, int expected) {
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class)).isEqualTo(expected);
    }
}
