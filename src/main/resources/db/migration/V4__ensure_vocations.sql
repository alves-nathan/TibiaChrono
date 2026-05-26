INSERT INTO vocations(name, promotion_name)
SELECT 'none', 'none'
WHERE NOT EXISTS (
    SELECT 1 FROM vocations WHERE lower(name) = 'none'
);

INSERT INTO vocations(name, promotion_name)
SELECT 'druid', 'elder druid'
WHERE NOT EXISTS (
    SELECT 1 FROM vocations WHERE lower(name) = 'druid' OR lower(promotion_name) = 'elder druid'
);

INSERT INTO vocations(name, promotion_name)
SELECT 'sorcerer', 'master sorcerer'
WHERE NOT EXISTS (
    SELECT 1 FROM vocations WHERE lower(name) = 'sorcerer' OR lower(promotion_name) = 'master sorcerer'
);

INSERT INTO vocations(name, promotion_name)
SELECT 'knight', 'elite knight'
WHERE NOT EXISTS (
    SELECT 1 FROM vocations WHERE lower(name) = 'knight' OR lower(promotion_name) = 'elite knight'
);

INSERT INTO vocations(name, promotion_name)
SELECT 'paladin', 'royal paladin'
WHERE NOT EXISTS (
    SELECT 1 FROM vocations WHERE lower(name) = 'paladin' OR lower(promotion_name) = 'royal paladin'
);

INSERT INTO vocations(name, promotion_name)
SELECT 'monk', 'exalted monk'
WHERE NOT EXISTS (
    SELECT 1 FROM vocations WHERE lower(name) = 'monk' OR lower(promotion_name) = 'exalted monk'
);
