# Letras en el iPod nano 6G/7G

## Qué se sabe (con fuente)
- **Tu biblioteca real (diagnóstico):** `Library.itdb` → `item.has_lyrics` (40 de 822 canciones = 1). `Extras.itdb` → tabla `lyrics(item_pid, checksum, lyrics)`: las 40 canciones con `has_lyrics=1` tienen fila, todas con el texto vacío (NULL); otras 50 filas son de canciones que ya no existen. El `checksum` es un entero que se repite entre canciones sincronizadas juntas: su significado NO se conoce.
- **El texto de la letra va en el archivo:** etiqueta ID3 `USLT` (MP3) — Macworld y la investigación de iOpenPod (classic).
- **El firmware mira primero la marca de la base de datos** y después lee la etiqueta (explicación de un mantenedor de libgpod, iPod 5G; traza del firmware del classic en iOpenPod: bit 0 de un byte de la ficha). Etiqueta sin marca = no se ve.
- **Límite:** el classic lee como máximo 10 240 bytes del cuadro de letra. Se corta la letra en un salto de línea para no pasarse.
- **Cómo verla en el nano táctil:** tocar la carátula y deslizar a la derecha dos veces (Macworld, 2011).
- Fuentes: iOpenPod `docs/research/itunesdb-lyrics.md`, `lyrics-firmware-adoption.md`, `docs/adr/0206…`; ipodsync (no trata letras); libgpod.

## Qué hace la app (0.35.1)
1. Escribe la letra en la etiqueta USLT de la copia que va al iPod (Latin‑1 si cabe, si no UTF‑16 con BOM; v2.3/2.4 respetadas).
2. Pone `item.has_lyrics = 1` y añade la fila en `Extras.itdb.lyrics` (item_pid, checksum vacío, texto vacío).
3. Las canciones nuevas ya no heredan `has_lyrics` de la fila plantilla (antes podían heredarlo).

## Incertidumbres (no probado en un nano 6G real)
- No se sabe si el firmware exige un `checksum` concreto en la fila de `lyrics`; se deja vacío. Si la letra no aparece: probar sin la fila (solo la marca) o copiar el valor de un `checksum` de una canción con letra.
- No se sabe si lee el texto de la columna `lyrics` o del archivo; se asume el archivo (como en las 40 canciones reales).
- Codificación y límite de tamaño en el nano 6G: asumidos como en el classic.

# Playlists de Qobuz en el iPod (0.36.0)
Fuente: ipodsync (MIT, confirmado en nano 7G): playlist = fila en `Library.itdb.container` + filas en `item_to_container` + fila en `Dynamic.itdb.container_ui`; `Locations.itdb` no cambia. Tu iPod real no tiene playlists de usuario (solo la maestra, tres integradas y Podcasts), así que la fila se construye desde cero: distinguished_kind 0, parent_pid 0, is_hidden 0, media_kinds 1 (valor ELEGIDO por mí, no verificado), container_ui = copia de la lista maestra (play_order 7, album_field_order 1).
Flujo: se elige la playlist de Qobuz → las canciones descargadas como MP3 que no estén en el iPod se envían → se crea (o se rellena, si ya existe una con ese nombre) la playlist con las canciones que haya en el iPod, en el orden de Qobuz. Si todas ya están en el iPod, solo se crea la playlist. NO probado en un nano 6G.
