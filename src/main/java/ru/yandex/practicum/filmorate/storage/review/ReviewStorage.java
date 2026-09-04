package ru.yandex.practicum.filmorate.storage.review;

import ru.yandex.practicum.filmorate.model.Review;

import java.util.Collection;
import java.util.Optional;

public interface ReviewStorage {
    Review create(Review review);

    Review update(Review review);

    void delete(Long id);

    Optional<Review> findById(Long id);

    Collection<Review> findReviews(Long filmId, int count);

    void addLikeOrDislike(Long reviewId, Long userId, boolean isLike);

    void deleteLikeOrDislike(Long reviewId, Long userId, boolean isLike);
}
