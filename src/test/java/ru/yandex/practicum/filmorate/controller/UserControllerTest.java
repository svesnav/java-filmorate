package ru.yandex.practicum.filmorate.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.model.Mpa;
import ru.yandex.practicum.filmorate.model.Review;
import ru.yandex.practicum.filmorate.model.User;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private User validUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setEmail("user_" + suffix + "@mail.ru");
        user.setLogin("login_" + suffix);
        user.setName("Nick Name");
        user.setBirthday(LocalDate.of(1990, 1, 1));
        return user;
    }

    private Film validFilm() {
        Film film = new Film();
        film.setName("Feed Film");
        film.setDescription("Description");
        film.setReleaseDate(LocalDate.of(2000, 1, 1));
        film.setDuration(120);
        Mpa mpa = new Mpa();
        mpa.setId(1);
        film.setMpa(mpa);
        return film;
    }

    @Test
    void shouldCreateUser() throws Exception {
        User user = validUser();
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value(user.getEmail()));
    }

    @Test
    void shouldNotCreateUserWithEmptyEmail() throws Exception {
        User user = validUser();
        user.setEmail("");
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldNotCreateUserWithEmailWithoutAt() throws Exception {
        User user = validUser();
        user.setEmail("mailmail.ru");
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldNotCreateUserWithEmptyLogin() throws Exception {
        User user = validUser();
        user.setLogin("");
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldNotCreateUserWithLoginContainingSpaces() throws Exception {
        User user = validUser();
        user.setLogin("dolore ullamco");
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldUseLoginAsNameWhenNameIsEmpty() throws Exception {
        User user = validUser();
        user.setName("");
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(user.getLogin()));
    }

    @Test
    void shouldNotCreateUserWithBirthdayInFuture() throws Exception {
        User user = validUser();
        user.setBirthday(LocalDate.of(3000, 1, 1));
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldGetAllUsers() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void shouldUpdateUser() throws Exception {
        User user = validUser();
        String response = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        User created = objectMapper.readValue(response, User.class);
        created.setEmail("new@mail.ru");

        mockMvc.perform(put("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(created)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@mail.ru"));
    }

    @Test
    void shouldNotUpdateNonExistentUser() throws Exception {
        User user = validUser();
        user.setId(999);
        mockMvc.perform(put("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnFeedEventsInExecutionOrder() throws Exception {
        User user = createUser();
        User friend = createUser();
        Film film = createFilm();

        mockMvc.perform(put("/users/{id}/friends/{friendId}", user.getId(), friend.getId()))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/users/{id}/friends/{friendId}", user.getId(), friend.getId()))
                .andExpect(status().isOk());
        mockMvc.perform(put("/films/{id}/like/{userId}", film.getId(), user.getId()))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/films/{id}/like/{userId}", film.getId(), user.getId()))
                .andExpect(status().isOk());

        Review review = new Review();
        review.setContent("Feed review");
        review.setIsPositive(true);
        review.setUserId(user.getId());
        review.setFilmId(film.getId());
        String response = mockMvc.perform(post("/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(review)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        review = objectMapper.readValue(response, Review.class);
        review.setContent("Updated feed review");
        mockMvc.perform(put("/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(review)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/reviews/{id}", review.getReviewId()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/users/{id}/feed", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].eventType").value("FRIEND"))
                .andExpect(jsonPath("$[0].operation").value("ADD"))
                .andExpect(jsonPath("$[0].entityId").value(friend.getId()))
                .andExpect(jsonPath("$[1].eventType").value("FRIEND"))
                .andExpect(jsonPath("$[1].operation").value("REMOVE"))
                .andExpect(jsonPath("$[2].eventType").value("LIKE"))
                .andExpect(jsonPath("$[2].operation").value("ADD"))
                .andExpect(jsonPath("$[2].entityId").value(film.getId()))
                .andExpect(jsonPath("$[3].eventType").value("LIKE"))
                .andExpect(jsonPath("$[3].operation").value("REMOVE"))
                .andExpect(jsonPath("$[4].eventType").value("REVIEW"))
                .andExpect(jsonPath("$[4].operation").value("ADD"))
                .andExpect(jsonPath("$[4].entityId").value(review.getReviewId()))
                .andExpect(jsonPath("$[5].eventType").value("REVIEW"))
                .andExpect(jsonPath("$[5].operation").value("UPDATE"))
                .andExpect(jsonPath("$[6].eventType").value("REVIEW"))
                .andExpect(jsonPath("$[6].operation").value("REMOVE"))
                .andExpect(jsonPath("$[0].userId").value(user.getId()))
                .andExpect(jsonPath("$[0].eventId").isNumber())
                .andExpect(jsonPath("$[0].timestamp").isNumber());
    }

    @Test
    void shouldNotReturnFeedForNonExistentUser() throws Exception {
        mockMvc.perform(get("/users/999999/feed"))
                .andExpect(status().isNotFound());
    }

    private User createUser() throws Exception {
        String response = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validUser())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(response, User.class);
    }

    private Film createFilm() throws Exception {
        String response = mockMvc.perform(post("/films")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validFilm())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(response, Film.class);
    }
}
