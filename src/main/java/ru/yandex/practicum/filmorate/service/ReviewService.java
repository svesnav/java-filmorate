package ru.yandex.practicum.filmorate.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.exception.ValidationException;
import ru.yandex.practicum.filmorate.model.EventType;
import ru.yandex.practicum.filmorate.model.FeedEvent;
import ru.yandex.practicum.filmorate.model.Operation;
import ru.yandex.practicum.filmorate.model.Review;
import ru.yandex.practicum.filmorate.storage.feed.FeedStorage;
import ru.yandex.practicum.filmorate.storage.film.FilmStorage;
import ru.yandex.practicum.filmorate.storage.review.ReviewStorage;
import ru.yandex.practicum.filmorate.storage.user.UserStorage;

import java.util.Collection;

@Service
public class ReviewService {
    private final ReviewStorage reviewStorage;
    private final UserStorage userStorage;
    private final FilmStorage filmStorage;
    private final FeedStorage feedStorage;

    private static final int DEFAULT_LIMIT = 10;

    @Autowired
    public ReviewService(@Qualifier("reviewDbStorage") ReviewStorage reviewStorage,
                         @Qualifier("userDbStorage") UserStorage userStorage,
                         @Qualifier("filmDbStorage") FilmStorage filmStorage,
                         @Qualifier("feedDbStorage") FeedStorage feedStorage) {
        this.reviewStorage = reviewStorage;
        this.userStorage = userStorage;
        this.filmStorage = filmStorage;
        this.feedStorage = feedStorage;
    }

    public Review create(Review review) {
        validate(review);
        if (review.getUserId() == null) {
            throw new ValidationException("User id must be specified");
        }
        if (review.getFilmId() == null) {
            throw new ValidationException("Film id must be specified");
        }
        userStorage.findById(review.getUserId())
                .orElseThrow(() -> new NotFoundException("User with id " + review.getUserId() + " not found"));
        filmStorage.findById(review.getFilmId())
                .orElseThrow(() -> new NotFoundException("Film with id " + review.getFilmId() + " not found"));
        Review created = reviewStorage.create(review);
        addFeedEvent(created.getUserId(), Operation.ADD, created.getReviewId());
        return created;
    }

    public Review update(Review review) {
        validate(review);
        if (review.getReviewId() == null) {
            throw new ValidationException("Review id must be specified");
        }
        Review oldReview = findById(review.getReviewId());
        oldReview.setContent(review.getContent());
        oldReview.setIsPositive(review.getIsPositive());
        Review updated = reviewStorage.update(oldReview);
        addFeedEvent(updated.getUserId(), Operation.UPDATE, updated.getReviewId());
        return updated;
    }

    public void delete(Long id) {
        Review review = findById(id);
        reviewStorage.delete(id);
        addFeedEvent(review.getUserId(), Operation.REMOVE, review.getReviewId());
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

    private void validate(Review review) {
        if (review.getContent() == null || review.getContent().isBlank()) {
            throw new ValidationException("Review content cannot be empty");
        }
        if (review.getContent().length() > 1000) {
            throw new ValidationException("Review content must be 1000 characters or less");
        }
        if (review.getIsPositive() == null) {
            throw new ValidationException("Review isPositive must be specified");
        }
    }

    private void addFeedEvent(long userId, Operation operation, long reviewId) {
        feedStorage.add(new FeedEvent(System.currentTimeMillis(), userId, EventType.REVIEW,
                operation, 0, reviewId));
    }
}
