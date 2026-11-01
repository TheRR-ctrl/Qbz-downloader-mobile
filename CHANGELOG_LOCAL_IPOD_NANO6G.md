# Cambios locales — gestión iPod nano 6G

## Implementado en esta revisión
- La pestaña **En el iPod** permite buscar canciones, seleccionar individualmente, seleccionar los resultados visibles y borrar en lote las pistas seleccionadas.
- El borrado usa el flujo existente de mantenimiento de la biblioteca: copia de seguridad de las bases de datos, eliminación de las filas relacionadas y borrado de los archivos de audio correspondientes. No borra carpetas ni archivos internos arbitrarios.
- Al insertar una canción nueva, se limpian los campos de referencia de carátula heredados de la fila plantilla (`artwork_status`, `artwork_cache_id` y referencias equivalentes de nuevos registros de álbum/artista). Esto evita asociar accidentalmente una carátula ajena.
- Se añadió una prueba unitaria para comprobar que una canción nueva no hereda la carátula de la fila usada como plantilla.

## Limitación importante
Esta revisión **todavía no implementa la generación ni la escritura de carátulas en `iPod_Control/Artwork/ArtworkDB` y los archivos `.ithmb`**. El informe del Nano 6G confirma que esos componentes existen, pero no proporciona por sí solo el formato binario necesario para escribirlos con seguridad. Por tanto, no debe considerarse resuelto el problema de portadas ni usarse esta revisión como prueba de exportación completa de carátulas.

## Validación
Se intentó ejecutar `:app:testDebugUnitTest --offline`, pero Gradle Wrapper no pudo arrancar porque la distribución Gradle 8.14.3 no está instalada en caché y el entorno no tiene acceso a `services.gradle.org`. No se pudo compilar ni ejecutar pruebas en este entorno.
