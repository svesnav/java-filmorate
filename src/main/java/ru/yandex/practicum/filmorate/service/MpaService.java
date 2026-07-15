package ru.yandex.practicum.filmorate.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.model.Mpa;
import ru.yandex.practicum.filmorate.storage.mpa.MpaStorage;

import java.util.List;

@Slf4j
@Service
public class MpaService {
    private final MpaStorage mpaStorage;

    @Autowired
    public MpaService(@Qualifier("mpaDbStorage") MpaStorage mpaStorage) {
        this.mpaStorage = mpaStorage;
    }

    public List<Mpa> findAll() {
        log.info("Showed all mpa ratings");
        return mpaStorage.findAll().stream().toList();
    }

    public Mpa findById(int id) {
        log.info("Showed mpa rating with id {}", id);
        return mpaStorage.findById(id)
                .orElseThrow(() -> {
                    log.warn("Mpa rating not found: {}", id);
                    return new NotFoundException("Mpa rating with id " + id + " not found");
                });
    }
}
