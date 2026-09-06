package ru.yandex.practicum.filmorate.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.model.Mpa;
import ru.yandex.practicum.filmorate.model.User;
import ru.yandex.practicum.filmorate.storage.film.FilmDbStorage;
import ru.yandex.practicum.filmorate.storage.user.UserDbStorage;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:review-validation;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class ReviewControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserDbStorage userStorage;

    @Autowired
    private FilmDbStorage filmStorage;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long userId;
    private long filmId;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setEmail("review@mail.ru");
        user.setLogin("review");
        user.setBirthday(LocalDate.of(1990, 1, 1));
        userId = userStorage.add(user).getId();

        Film film = new Film();
        film.setName("Review film");
        film.setReleaseDate(LocalDate.of(2000, 1, 1));
        film.setDuration(120);
        Mpa mpa = new Mpa();
        mpa.setId(1);
        film.setMpa(mpa);
        filmId = filmStorage.add(film).getId();
    }

    @ParameterizedTest
    @ValueSource(strings = {"content", "isPositive", "userId", "filmId"})
    void shouldRejectMissingCreateField(String field) throws Exception {
        ObjectNode review = validReview();
        review.remove(field);

        mockMvc.perform(post("/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content(review.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reviews", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM feed_events", Integer.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"content", "isPositive", "userId", "filmId"})
    void shouldRejectNullCreateField(String field) throws Exception {
        ObjectNode review = validReview();
        review.putNull(field);

        mockMvc.perform(post("/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content(review.toString()))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void shouldRejectBlankContent(String content) throws Exception {
        ObjectNode review = validReview();
        review.put("content", content);

        mockMvc.perform(post("/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content(review.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectContentLongerThanDatabaseLimit() throws Exception {
        ObjectNode review = validReview();
        review.put("content", "a".repeat(1001));

        mockMvc.perform(post("/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content(review.toString()))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"userId", "filmId"})
    void shouldReturnNotFoundForNonExistentEntity(String field) throws Exception {
        ObjectNode review = validReview();
        review.put(field, Long.MAX_VALUE);

        mockMvc.perform(post("/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content(review.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").isString());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reviews", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM feed_events", Integer.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"userId", "filmId"})
    void shouldReturnNotFoundForNegativeEntityId(String field) throws Exception {
        ObjectNode review = validReview();
        review.put(field, -1);

        mockMvc.perform(post("/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content(review.toString()))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"reviewId", "content", "isPositive"})
    void shouldRejectMissingUpdateFieldWithoutChangingReview(String field) throws Exception {
        long reviewId = createReview();
        ObjectNode review = validReview();
        review.put("reviewId", reviewId);
        review.put("content", "Changed content");
        review.remove(field);

        mockMvc.perform(put("/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content(review.toString()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/reviews/{id}", reviewId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Review content"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM feed_events", Integer.class)).isEqualTo(1);
    }

    @Test
    void shouldReturnNotFoundWhenUpdatingNonExistentReview() throws Exception {
        ObjectNode review = validReview();
        review.put("reviewId", Long.MAX_VALUE);

        mockMvc.perform(put("/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content(review.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldUpdateWithoutChangingAuthorOrFilm() throws Exception {
        long reviewId = createReview();
        ObjectNode review = validReview();
        review.put("reviewId", reviewId);
        review.put("content", "Updated content");
        review.put("userId", Long.MAX_VALUE);
        review.put("filmId", Long.MAX_VALUE);

        mockMvc.perform(put("/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content(review.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Updated content"))
                .andExpect(jsonPath("$.isPositive").value(false))
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.filmId").value(filmId));

        review.remove("userId");
        review.remove("filmId");
        mockMvc.perform(put("/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content(review.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void shouldNotConsumeReviewIdOnValidationFailure() throws Exception {
        long firstId = createReview();
        ObjectNode invalidReview = validReview();
        invalidReview.remove("content");
        mockMvc.perform(post("/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content(invalidReview.toString()))
                .andExpect(status().isBadRequest());

        assertThat(createReview()).isEqualTo(firstId + 1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM feed_events", Integer.class)).isEqualTo(2);
    }

    private ObjectNode validReview() {
        ObjectNode review = objectMapper.createObjectNode();
        review.put("content", "Review content");
        review.put("isPositive", false);
        review.put("userId", userId);
        review.put("filmId", filmId);
        return review;
    }

    @ParameterizedTest
    @ValueSource(strings = {"like", "dislike"})
    void shouldDeleteRatedReviewAndRecordFeedEvent(String rating) throws Exception {
        long reviewId = createReview();
        long remainingReviewId = createReview();
        mockMvc.perform(put("/reviews/{id}/{rating}/{userId}", reviewId, rating, userId))
                .andExpect(status().isOk());
        mockMvc.perform(put("/reviews/{id}/{rating}/{userId}", remainingReviewId, rating, userId))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/reviews/{id}", reviewId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/reviews/{id}", reviewId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/reviews").param("filmId", String.valueOf(filmId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].reviewId").value(remainingReviewId));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM review_likes WHERE review_id = ?", Integer.class, reviewId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM review_likes WHERE review_id = ?", Integer.class, remainingReviewId)).isEqualTo(1);
        mockMvc.perform(get("/users/{id}/feed", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[2].eventType").value("REVIEW"))
                .andExpect(jsonPath("$[2].operation").value("REMOVE"))
                .andExpect(jsonPath("$[2].entityId").value(reviewId));
    }

    @Test
    void shouldRecordRepeatedLikeBeforeReviewEvents() throws Exception {
        mockMvc.perform(put("/films/{id}/like/{userId}", filmId, userId))
                .andExpect(status().isOk());
        mockMvc.perform(put("/films/{id}/like/{userId}", filmId, userId))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM film_likes WHERE film_id = ? AND user_id = ?",
                Integer.class, filmId, userId)).isEqualTo(1);

        long reviewId = createReview();
        ObjectNode review = validReview();
        review.put("reviewId", reviewId);
        review.put("content", "Updated review");
        mockMvc.perform(put("/reviews").contentType(MediaType.APPLICATION_JSON).content(review.toString()))
                .andExpect(status().isOk());
        mockMvc.perform(put("/reviews/{id}/like/{userId}", reviewId, userId))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/reviews/{id}", reviewId))
                .andExpect(status().isOk());

        String response = mockMvc.perform(get("/users/{id}/feed", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].eventType").value("LIKE"))
                .andExpect(jsonPath("$[0].operation").value("ADD"))
                .andExpect(jsonPath("$[1].eventType").value("LIKE"))
                .andExpect(jsonPath("$[1].operation").value("ADD"))
                .andExpect(jsonPath("$[1].entityId").value(filmId))
                .andExpect(jsonPath("$[2].eventType").value("REVIEW"))
                .andExpect(jsonPath("$[2].operation").value("ADD"))
                .andExpect(jsonPath("$[3].operation").value("UPDATE"))
                .andExpect(jsonPath("$[4].eventType").value("REVIEW"))
                .andExpect(jsonPath("$[4].operation").value("REMOVE"))
                .andExpect(jsonPath("$[4].entityId").value(reviewId))
                .andReturn().getResponse().getContentAsString();
        JsonNode events = objectMapper.readTree(response);
        long firstEventId = events.get(0).get("eventId").asLong();
        for (int i = 0; i < events.size(); i++) {
            assertThat(events.get(i).get("eventId").asLong()).isEqualTo(firstEventId + i);
        }
    }

    private long createReview() throws Exception {
        String response = mockMvc.perform(post("/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content(validReview().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPositive").value(false))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("reviewId").asLong();
    }
}
