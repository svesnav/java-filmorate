package ru.yandex.practicum.filmorate.storage.mpa;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@AutoConfigureTestDatabase
@Import(MpaDbStorage.class)
@RequiredArgsConstructor(onConstructor_ = @Autowired)
class MpaDbStorageTest {
    private final MpaDbStorage mpaStorage;

    @Test
    void testFindAllMpa() {
        assertThat(mpaStorage.findAll()).hasSize(5);
    }

    @Test
    void testFindMpaById() {
        assertThat(mpaStorage.findById(1))
                .isPresent()
                .hasValueSatisfying(mpa ->
                        assertThat(mpa.getName()).isEqualTo("G")
                );
    }

    @Test
    void testFindMpaByIdNotFound() {
        assertThat(mpaStorage.findById(999)).isEmpty();
    }
}
