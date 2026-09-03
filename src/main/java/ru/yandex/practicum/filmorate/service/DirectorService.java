package ru.yandex.practicum.filmorate.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.exception.ValidationException;
import ru.yandex.practicum.filmorate.model.Director;
import ru.yandex.practicum.filmorate.storage.director.DirectorStorage;

import java.util.Collection;

@Service
@RequiredArgsConstructor
public class DirectorService {
    private final DirectorStorage directorStorage;

    public Collection<Director> findAll() {
        return directorStorage.findAll();
    }

    public Director findById(long id) {
        return directorStorage.findById(id)
                .orElseThrow(() -> new NotFoundException("Director with id " + id + " not found"));
    }

    public Director create(Director director) {
        validate(director);
        return directorStorage.add(director);
    }

    public Director update(Director director) {
        validate(director);
        return directorStorage.update(director);
    }

    public void delete(long id) {
        directorStorage.delete(id);
    }

    private void validate(Director director) {
        if (director.getName() == null || director.getName().isBlank()) {
            throw new ValidationException("Director name cannot be empty");
        }
    }
}
