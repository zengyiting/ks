SELECT id, username, HEX(username) AS username_hex
FROM users
ORDER BY id
LIMIT 20;

SELECT id, name, HEX(name) AS name_hex, category
FROM items
ORDER BY id
LIMIT 30;
