package ru.yandex.practicum.filmorate.storage.mpa;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.filmorate.model.Mpa;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@Qualifier("mpaDbStorage")
@RequiredArgsConstructor
public class MpaDbStorage implements MpaStorage {
    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Mpa> mpaRowMapper = (rs, rowNum) -> {
        Mpa mpa = new Mpa();
        mpa.setId(rs.getInt("mpa_id"));
        mpa.setName(rs.getString("name"));
        return mpa;
    };

    @Override
    public Collection<Mpa> findAll() {
        return jdbcTemplate.query("SELECT mpa_id, name FROM mpa ORDER BY mpa_id", mpaRowMapper);
    }

    @Override
    public Optional<Mpa> findById(int id) {
        List<Mpa> mpas = jdbcTemplate.query(
                "SELECT mpa_id, name FROM mpa WHERE mpa_id = ?",
                mpaRowMapper, id);
        return mpas.stream().findFirst();
    }
}
