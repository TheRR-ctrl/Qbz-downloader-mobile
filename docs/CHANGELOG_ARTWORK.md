# Cambios 0.33.0 – carátulas del iPod nano 6G
- Nuevo `Nano6GArtworkDb`: lector/escritor de ArtworkDB que conserva byte a byte los registros existentes y rechaza estructuras desconocidas.
- Nuevo `Nano6GArtworkUpdater`: transacción (comprobar → copia de seguridad → añadir fotogramas a los 4 .ithmb → nuevo ArtworkDB → verificar) con rollback.
- `Nano6GCoverFrames`: recorte cuadrado y escalado a 240/88/50/58 px, RGB565 little-endian.
- `ItlpLibrary`: `setArtwork`, `artworkIdFor` (reutiliza la carátula de un álbum ya presente), `artworkReferences`.
- `IpodDevice.send`: tras añadir las canciones, escribe las carátulas (un fallo de carátulas nunca anula el envío) y las deshace si falla la biblioteca.
- `SendItem.coverUrl`, `IpodCovers` (descarga una vez, caché en disco), mensaje con el nº de carátulas escritas.
- Pruebas: ArtworkDB real del diagnóstico como fixture (lectura, ida y vuelta idéntica, añadir, ids, offsets, validación, rechazo de estructuras dañadas, rollback, referencias con Library.itdb).
