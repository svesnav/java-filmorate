package ru.yandex.practicum.filmorate.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.model.Review;
import ru.yandex.practicum.filmorate.storage.film.FilmStorage;
import ru.yandex.practicum.filmorate.storage.review.ReviewStorage;
import ru.yandex.practicum.filmorate.storage.user.UserStorage;

import java.util.Collection;

@Service
public class ReviewService {
    private final ReviewStorage reviewStorage;
    private final UserStorage userStorage;
    private final FilmStorage filmStorage;

    private static final int DEFAULT_LIMIT = 10;

    @Autowired
    public ReviewService(@Qualifier("reviewDbStorage") ReviewStorage reviewStorage,
                         @Qualifier("userDbStorage") UserStorage userStorage,
                         @Qualifier("filmDbStorage") FilmStorage filmStorage) {
        this.reviewStorage = reviewStorage;
        this.userStorage = userStorage;
        this.filmStorage = filmStorage;
    }

    public Review create(Review review) {
        userStorage.findById(review.getUserId()).orElseThrow(() -> new NotFoundException("User not found"));
        filmStorage.findById(review.getFilmId()).orElseThrow(() -> new NotFoundException("Film not found"));
        return reviewStorage.create(review);
    }

    public Review update(Review review) {
        Review oldReview = findById(review.getReviewId());
        oldReview.setContent(review.getContent());
        oldReview.setIsPositive(review.getIsPositive());
        return reviewStorage.update(oldReview);
    }

    public void delete(Long id) {
        reviewStorage.delete(id);
    }

    public Review findById(Long id) {
        return reviewStorage.findById(id)
                .orElseThrow(() -> new NotFoundException("Review with id = " + id + " not found"));
    }

    public Collection<Review> getReviews(Long filmId, Integer count) {
        int limit = (count != null) ? count : DEFAULT_LIMIT;
        return reviewStorage.findReviews(filmId, limit);
    }

    public void addLike(Long id, Long userId) {
        reviewStorage.addLikeOrDislike(id, userId, true);
    }

    public void addDislike(Long id, Long userId) {
        reviewStorage.addLikeOrDislike(id, userId, false);
    }

    public void deleteLike(Long id, Long userId) {
        reviewStorage.deleteLikeOrDislike(id, userId, true);
    }

    public void deleteDislike(Long id, Long userId) {
        reviewStorage.deleteLikeOrDislike(id, userId, false);
    }
}