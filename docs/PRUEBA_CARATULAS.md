# Instalación y prueba de las carátulas (no probado en hardware)
1. Instala la APK 0.33.0 de QBDLX Mobile y abre la pantalla del iPod con el iPod conectado.
2. Usa "Copia de seguridad" / el informe técnico antes de enviar (se hace una copia automática de las bases; la de ArtworkDB va en files/ipod-artwork-backups).
3. Envía UNA canción de un álbum con carátula que no esté en el iPod. El mensaje final debe decir "Carátulas escritas: 1".
4. Expulsa el iPod y mira si la canción muestra la carátula. Si el iPod se comporta mal: "Restaurar la última copia" y avísame; después genera el informe técnico.
5. Para comprobarlo en el PC: copia iPod_Control/Artwork y verifica que F1073_1.ithmb creció 115200 bytes y los otros 15488, 5000 y 6728.

## Confirmado en hardware
Las carátulas se escriben y se ven en un iPod nano 6G real (confirmado por el usuario, 0.33.5). Se guarda una carátula por álbum (mismo álbum+artista), compartida por sus canciones. Aún no probado: sincronizar después con iTunes.

## Letras dentro del MP3 (0.34.0)
Interruptor en la pantalla del iPod: escribe la letra (texto simple de LRCLIB) en la etiqueta ID3 USLT de la copia enviada. Útil en iPod que muestran letras (classic, nano 3G–5G); el nano 6G/7G no las muestran. Sin probar en un aparato: solo hay pruebas de que la etiqueta queda bien escrita. Para el classic faltaría además su base de datos (iTunesDB), que esta app no escribe.

## Carátulas en la vista de Álbumes (0.36.4)
La vista de Álbumes del iPod busca la portada con `album.artwork_item_pid` (una canción del álbum que tenga carátula), como la escribe iTunes (en la biblioteca real: 212 de 212 álbumes con puntero apuntan a una canción con carátula; `album.artwork_status` sigue en 0). Antes los álbumes nuevos se quedaban con 0. Ahora se rellena al escribir carátulas, y el botón «Reparar carátulas de álbumes» (pestaña «En el iPod») arregla las canciones ya enviadas. Sin probar en un nano 6G.
