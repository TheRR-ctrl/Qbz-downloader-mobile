# Limitaciones conocidas
- No se ha probado en un iPod nano 6G real: el iPod puede rechazar o ignorar los registros añadidos.
- Campos sin interpretar (mhni+36, parte de mhfd, mhii+48/+60/+76/+84, motivo de los 1073 repetidos): se copian de registros reales o se dejan a 0.
- Añadir y cortar los .ithmb necesita un archivo con acceso aleatorio (openFileDescriptor "rw"); si el proveedor del iPod no lo da, las carátulas se omiten y el envío continúa sin ellas.
- ArtworkDB se reemplaza por escritura directa (con copia y verificación posterior), no por renombrado atómico: un corte de corriente justo ahí puede dejarlo incompleto; la copia permite recuperarlo.
- Si la app se cierra a mitad de la escritura, los .ithmb pueden quedar más largos que los registros; el siguiente envío lo detecta y no escribe carátulas (no borra datos desconocidos).
- No hay interfaz para restaurar solo ArtworkDB; la copia queda en files/ipod-artwork-backups.
- Los registros huérfanos (229 en el diagnóstico) y los fotogramas 1073 repetidos no se limpian.
- La carátula se toma de la URL del álbum; sin red o sin imagen, la canción se envía sin carátula.
