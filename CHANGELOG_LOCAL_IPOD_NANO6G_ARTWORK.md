# Integración experimental de carátulas — iPod nano 6G

## Incluido
- Nuevo `Nano6GArtworkEncoder`: convierte píxeles ARGB8888 a RGB565 little-endian.
- Perfiles observados en el diagnóstico real del usuario:
  - `F1073_1.ithmb`: 240×240
  - `F1085_1.ithmb`: 88×88
  - `F1074_1.ithmb`: 50×50
  - `F1089_1.ithmb`: 58×58
- Pruebas unitarias de dimensiones, longitud de frame, conversión RGB565 y byte order.

## No habilitado (deliberadamente)
Esta revisión NO escribe ni modifica `ArtworkDB`, los archivos `.ithmb`, ni los campos de artwork de `Library.itdb`. La evidencia disponible confirma perfiles de píxel y referencias existentes, pero no basta para demostrar el formato exacto de cada registro `mhii`/`mhni`, alineación/padding de frames, reglas de asignación de offsets, ni la secuencia atómica que el firmware acepta. Insertar solo un frame o marcar `artwork_status=1` podría dejar referencias rotas.

El codificador queda como componente aislado para pruebas y no se invoca desde `IpodDevice.send()`. La exportación de pistas mantiene el comportamiento anterior y no promete carátulas en el dispositivo.

## Validación
Se añadieron pruebas JUnit. No se declara compilación ni ejecución satisfactoria de Gradle en este entorno; debe ejecutarse `./gradlew :app:testDebugUnitTest` y `./gradlew :app:assembleDebug` en un entorno Android con JDK 17/SDK configurados.
