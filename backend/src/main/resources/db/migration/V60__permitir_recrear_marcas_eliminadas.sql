-- Igual que V59 para categorías: una marca eliminada ya no bloquea crear otra con el mismo nombre.
ALTER TABLE marcas
DROP CONSTRAINT IF EXISTS uq_marcas_nombre;

-- Nombres únicos solo entre las marcas no eliminadas, sin distinguir mayúsculas ni espacios.
CREATE UNIQUE INDEX IF NOT EXISTS uq_marcas_nombre_activo
ON marcas (LOWER(TRIM(nombre)))
WHERE eliminado = FALSE;
