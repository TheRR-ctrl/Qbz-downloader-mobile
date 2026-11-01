# ArtworkDB / ITHMB del iPod nano 6G – análisis real de ArtworkDB / ITHMB del iPod nano 6G

Fuente: diagnóstico del usuario (ArtworkDB 473 072 B, 4 ITHMB, 5 ITDB). F1073_1.ithmb reconstruido
(SHA-256 verificado). Todo se analizó sobre copias, en modo solo lectura. Nada modificó el iPod ni el proyecto.
Certeza: ALTA = comprobado con los datos; MEDIA = coherente con los datos pero sin prueba cruzada; BAJA = campo sin interpretar.

## 1. Estructura de ArtworkDB (little-endian)
mhfd (cabecera 132 B, total 473 072) → 3 × mhsd (96 B):
- mhsd tipo 1 → mhli (92 B, 412 hijos) con 412 × mhii (imágenes)      [ALTA]
- mhsd tipo 2 → mhla (92 B, 0 hijos): lista de álbumes VACÍA            [ALTA]
- mhsd tipo 3 → mhlf (92 B, 4 hijos): 4 × mhif (124 B), perfiles de formato [ALTA]

mhfd: +4 tamaño cab., +8 tamaño total, +20 nº de mhsd (3) [ALTA]; +28 = 512 = max(id mhii)+1 → siguiente id [MEDIA-ALTA];
+16 = 6 (versión/?) [BAJA]; +32/+40/+60 (8 bytes cada uno, no nulos) sin interpretar, ¿identificador/firma? [BAJA].

mhif (perfiles): +16 id de formato, +20 tamaño del fotograma en bytes:
| id | tamaño | = | archivo |
|1073|115 200|240×240×2|F1073_1.ithmb|
|1085| 15 488| 88×88×2 |F1085_1.ithmb|
|1074|  5 000| 50×50×2 |F1074_1.ithmb|
|1089|  6 728| 58×58×2 |F1089_1.ithmb|  [ALTA]

mhii (152 B cab.): +12 nº de hijos; +16 id (u32, único, 100..511); +20 u64 = pid del item de Library.itdb que la creó
(coincide con item.pid en 169 de 268 casos; el resto no coincide: significado exacto BAJA); +48 fecha (digdate) [MEDIA].
Hijos: mhod tipo 2 (uno por fotograma) y un mhod tipo 6 final con mhaf (60 B, vacío) [ALTA].
mhod tipo 2: cab. 24 B, total 164 B = mhni (76 B cab., total 140) + mhod tipo 3 con el nombre ":F1073_1.ithmb" UTF-16LE [ALTA].
mhni: +16 id de formato; +20 offset en el ITHMB; +24 tamaño; +28 i16 pad vertical (0); +30 i16 pad horizontal (0);
+32 u16 alto; +34 u16 ancho; +36 u32 ¿?; +40 tamaño repetido. [ALTA salvo +36]
mhni +36: en el primer lote de 1073 crece (0, 24000, 330000, …), en los demás es 0 o 0xFFFFFFFF [BAJA, no inventar].

Patrones de mhii: 380 registros con 5 hijos (1085,1073,1074,1089 + mhaf) → patrón normal.
32 registros con 21–24 hijos: 1073 aparece 16–19 veces (un mismo registro con muchos fotogramas 1073) [ALTA el hecho; la causa BAJA].
1085, 1074 y 1089 aparecen exactamente una vez por mhii (412 cada uno); 1073 aparece 958 veces.

## 2. ITHMB
- Fotogramas contiguos, sin relleno entre ellos: tamaño = ancho×alto×2. Todos los offsets son múltiplos del tamaño [ALTA].
- Cobertura 1:1: 958 (1073), 412 (1085), 412 (1074), 412 (1089) fotogramas; cada uno tiene exactamente un registro mni; sin huecos ni solapes [ALTA].
- Tamaños de archivo = nº de fotogramas × tamaño exacto (sin cola) [ALTA].
- Formato de píxel: RGB565. El orden LITTLE-ENDIAN produce imágenes suaves (diferencia media entre vecinos 12–17) frente a big-endian (38–40) → little-endian [ALTA, estadística; falta verlo en pantalla].
- Alineación/padding: ninguno observado (vpad=hpad=0, sin bytes de relleno) [ALTA].

## 3. Relación con Library.itdb (esquema real)
- item.artwork_status: 1 = con carátula (268), 2 = sin carátula (554). [ALTA]
- item.artwork_cache_id = mhii.id para los 268 items con carátula (0 en los demás); 183 ids distintos (hasta 18 items comparten un id: mismo álbum) [ALTA]
- album.artwork_status = 0 y artist.artwork_status = 0 en todas las filas; album.artwork_item_pid y artist.artwork_album_pid sin uso observado [ALTA]
- Ningún mhii está ligado a album/artist (mhla vacío).
- 229 de 412 mhii no los usa ningún item (huérfanos) [ALTA].

## 4. Pendiente / no demostrado
- Significado de mhni+36, mhfd+16/+32/+40/+60, mhii+20 cuando no coincide con item.pid, y del 1073 repetido.
- Si ArtworkDB lleva firma/hash: no hay evidencia en el archivo; no asumirlo ni recalcular nada.
- Si la rotación/orientación de los píxeles es la correcta: requiere ver los fotogramas (puedo renderizarlos a PNG para comprobarlo).
- Que el iPod acepte un mhii añadido: solo se prueba en el dispositivo.

## 5. Resultados adicionales de la Fase 2 (comprobados con el mismo diagnóstico)
- Contador de ids: mhfd+28 = 512 = max(id)+1; los ids 100..511 son contiguos y los registros están en orden de id [ALTA]. El código rechaza
  un ArtworkDB cuyo siguiente id colisione con uno existente y lo incrementa al añadir.
- mhii+56: contador pequeño que crece con el nº de canciones que usan la imagen, sin coincidir exactamente [MEDIA]. Al añadir se escribe el nº de canciones que la usan (≥1).
- mhii+48 (fecha), +76/+84 (0x7FF80000, parte de un NaN doble) y +60 (=1): sin interpretar; los registros nuevos copian los valores de un registro real de cinco hijos (la fecha se deja a 0) [BAJA].
- Pid discrepante (99 items): mhii+20 guarda el pid del PRIMER item que creó la imagen. 169 de los 183 ids usados tienen a ese item entre sus canciones;
  43 de los 99 discrepantes comparten id con ese item (mismo álbum), y los otros 56 pertenecen a 14 imágenes cuyo item de origen ya no existe (canción borrada/sustituida) [MEDIA-ALTA].
  Por tanto no son un error y NO se modifican. Ningún mhii huérfano (229) tiene su item de origen en la biblioteca.
- No se escribe mhla: está vacío en el dispositivo y la biblioteca no referencia álbumes en ArtworkDB.
- Los ITHMB 1085/1074/1089 siguen el orden de los registros (frame i = registro i); el 1073 no (hasta 19 fotogramas en 32 registros). Los registros nuevos añaden un fotograma por perfil al final de cada archivo.

## 6. Qué escribe QBDLX Mobile (0.33.0)
1. Comprueba: ArtworkDB se reconoce por completo, cada .ithmb termina exactamente donde acaba su último registro, todas las referencias son válidas. Si algo no cuadra, no escribe nada.
2. Copia ArtworkDB al teléfono (files/ipod-artwork-backups/<fecha>/ArtworkDB).
3. Añade a cada .ithmb los fotogramas nuevos (RGB565 little-endian, sin relleno).
4. Añade un mhii por carátula (id = mhfd+28, que se incrementa), con los cuatro fotogramas y su offset, y reescribe ArtworkDB tras validarlo.
5. En Library.itdb: item.artwork_status = 1 y item.artwork_cache_id = id del mhii, solo en las canciones nuevas. Las canciones de un álbum que ya tiene carátula en el iPod reutilizan su id.
6. Si algo falla después de empezar: los .ithmb se cortan a su tamaño anterior y ArtworkDB se restaura. Si falla la escritura de la biblioteca, también se deshacen las carátulas.
No se tocan Locations.itdb ni su .cbk por las carátulas (ya se firman por el envío de canciones); no se asume ni recalcula ningún hash de ArtworkDB.
