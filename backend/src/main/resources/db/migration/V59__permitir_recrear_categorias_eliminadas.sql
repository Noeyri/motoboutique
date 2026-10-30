-- Eliminar la restricción que impedía reutilizar nombres.
ALTER TABLE categorias
DROP CONSTRAINT IF EXISTS uq_categorias_nombre;

-- Evitar nombres duplicados únicamente entre categorías activas.
CREATE UNIQUE INDEX IF NOT EXISTS uq_categorias_nombre_activo
ON categorias (LOWER(TRIM(nombre)))
WHERE eliminado = FALSE;