package ru.yandex.practicum.filmorate.storage.review;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.model.Review;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
@Qualifier("reviewDbStorage")
@RequiredArgsConstructor
public class ReviewDbStorage implements ReviewStorage {
    private final JdbcTemplate jdbcTemplate;

    private final String baseSelectSql =
            "SELECT r.review_id, r.content, r.is_positive, r.user_id, r.film_id, " +
                    "COALESCE(SUM(CASE WHEN rl.is_like = TRUE THEN 1 WHEN rl.is_like = FALSE THEN -1 END), 0) AS useful_rating " +
                    "FROM reviews r " +
                    "LEFT JOIN review_likes rl ON r.review_id = rl.review_id ";

    private final String baseGroupBy = "GROUP BY r.review_id, r.content, r.is_positive, r.user_id, r.film_id ";

    @Override
    public Review create(Review review) {
        String sql = "INSERT INTO reviews (content, is_positive, user_id, film_id) VALUES (?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement stmt = connection.prepareStatement(sql, new String[]{"review_id"});
            stmt.setString(1, review.getContent());
            stmt.setBoolean(2, review.getIsPositive());
            stmt.setLong(3, review.getUserId());
            stmt.setLong(4, review.getFilmId());
            return stmt;
        }, keyHolder);

        long newId = Objects.requireNonNull(keyHolder.getKey()).longValue();
        review.setReviewId(newId);
        return findById(newId).orElse(review);
    }

    @Override
    public Review update(Review review) {
        String sql = "UPDATE reviews SET content = ?, is_positive = ? WHERE review_id = ?";
        int rows = jdbcTemplate.update(sql, review.getContent(), review.getIsPositive(), review.getReviewId());
        if (rows == 0) {
            throw new NotFoundException("Review with id = " + review.getReviewId() + " not found");
        }
        return findById(review.getReviewId()).orElse(review);
    }

    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM reviews WHERE review_id = ?";
        int rows = jdbcTemplate.update(sql, id);
        if (rows == 0) {
            throw new NotFoundException("Review with id = " + id + " not found");
        }
    }

    @Override
    public Optional<Review> findById(Long id) {
        String sql = baseSelectSql + "WHERE r.review_id = ? " + baseGroupBy;
        List<Review> reviews = jdbcTemplate.query(sql, this::mapRowToReview, id);
        return reviews.stream().findFirst();
    }

    @Override
    public Collection<Review> findReviews(Long filmId, int count) {
        String sql = baseSelectSql;
        Object[] args;

        if (filmId != null) {
            sql += "WHERE r.film_id = ? " + baseGroupBy + "ORDER BY useful_rating DESC, r.review_id ASC LIMIT ?";
            args = new Object[]{filmId, count};
        } else {
            sql += baseGroupBy + "ORDER BY useful_rating DESC, r.review_id ASC LIMIT ?";
            args = new Object[]{count};
        }
        return jdbcTemplate.query(sql, this::mapRowToReview, args);
    }

    @Override
    public void addLikeOrDislike(Long reviewId, Long userId, boolean isLike) {
        String sql = "MERGE INTO review_likes (review_id, user_id, is_like) KEY(review_id, user_id) VALUES (?, ?, ?)";
        jdbcTemplate.update(sql, reviewId, userId, isLike);
    }

    @Override
    public void deleteLikeOrDislike(Long reviewId, Long userId, boolean isLike) {
        String sql = "DELETE FROM review_likes WHERE review_id = ? AND user_id = ? AND is_like = ?";
        jdbcTemplate.update(sql, reviewId, userId, isLike);
    }

    private Review mapRowToReview(ResultSet rs, int rowNum) throws SQLException {
        Review review = new Review();
        review.setReviewId(rs.getLong("review_id"));
        review.setContent(rs.getString("content"));
        review.setIsPositive(rs.getBoolean("is_positive"));
        review.setUserId(rs.getLong("user_id"));
        review.setFilmId(rs.getLong("film_id"));
        review.setUseful(rs.getInt("useful_rating"));
        return review;
    }
}