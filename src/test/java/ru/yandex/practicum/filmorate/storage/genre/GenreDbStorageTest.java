package ru.yandex.practicum.filmorate.storage.genre;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import ru.yandex.practicum.filmorate.model.Genre;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@AutoConfigureTestDatabase
@Import(GenreDbStorage.class)
@RequiredArgsConstructor(onConstructor_ = @Autowired)
class GenreDbStorageTest {
    private final GenreDbStorage genreStorage;

    @Test
    void testFindAllGenres() {
        assertThat(genreStorage.findAll()).hasSize(6);
    }

    @Test
    void testFindGenreById() {
        assertThat(genreStorage.findById(1))
                .isPresent()
                .hasValueSatisfying(genre ->
                        assertThat(genre.getName()).isEqualTo("Комедия")
                );
    }

    @Test
    void testFindByIds() {
        assertThat(genreStorage.findByIds(List.of(1, 2, 3)))
                .hasSize(3)
                .extracting(Genre::getId)
                .containsExactly(1, 2, 3);
    }

    @Test
    void testFindByIdsReturnsPartialResultForMissingIds() {
        assertThat(genreStorage.findByIds(List.of(1, 999))).hasSize(1);
    }

    @Test
    void testFindGenreByIdNotFound() {
        assertThat(genreStorage.findById(999)).isEmpty();
    }
}
