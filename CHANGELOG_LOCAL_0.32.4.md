# QBDLX Mobile 0.32.4 — navegación y transparencia iPod nano 6G

## Cambios
- La barra inferior asigna explícitamente el mismo ancho a cada destino, incluido el acceso contextual al iPod.
- Etiquetas de navegación en una sola línea y tamaño compacto para reducir el amontonamiento visual en pantallas estrechas.
- Se actualiza versionCode a 93 y versionName a 0.32.4.
- El aviso de la pantalla iPod ahora indica claramente que la escritura de carátulas para nano 6G aún no está implementada y que las pistas transferidas mostrarán el icono genérico.

## Limitación funcional importante
Esta versión NO añade escritura de ArtworkDB ni de ITHMB. El codificador RGB565 experimental solo convierte píxeles; no crea registros mhii/mhni, no asigna offsets ni actualiza las referencias de álbumes/pistas. Por seguridad, no se anuncia ni habilita la sincronización de carátulas.

## Verificación
El código fuente se actualizó. La compilación y las pruebas deben ejecutarse en un entorno Android Gradle válido; no se afirma que esta APK haya sido compilada o probada en hardware.
