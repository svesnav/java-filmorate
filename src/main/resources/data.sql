MERGE INTO mpa (mpa_id, name) KEY (mpa_id) VALUES (1, 'G');
MERGE INTO mpa (mpa_id, name) KEY (mpa_id) VALUES (2, 'PG');
MERGE INTO mpa (mpa_id, name) KEY (mpa_id) VALUES (3, 'PG-13');
MERGE INTO mpa (mpa_id, name) KEY (mpa_id) VALUES (4, 'R');
MERGE INTO mpa (mpa_id, name) KEY (mpa_id) VALUES (5, 'NC-17');

MERGE INTO genres (genre_id, name) KEY (genre_id) VALUES (1, 'Комедия');
MERGE INTO genres (genre_id, name) KEY (genre_id) VALUES (2, 'Драма');
MERGE INTO genres (genre_id, name) KEY (genre_id) VALUES (3, 'Мультфильм');
MERGE INTO genres (genre_id, name) KEY (genre_id) VALUES (4, 'Триллер');
MERGE INTO genres (genre_id, name) KEY (genre_id) VALUES (5, 'Документальный');
MERGE INTO genres (genre_id, name) KEY (genre_id) VALUES (6, 'Боевик');

MERGE INTO users (user_id, email, login, name, birthday) KEY (user_id)
    VALUES (1, 'mail@mail.ru', 'dolore', 'Nick Name', DATE '1990-01-01');

ALTER TABLE users ALTER COLUMN user_id RESTART WITH 2;
