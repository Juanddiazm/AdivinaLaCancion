# Adivina la Canción

Juego de fiesta para Android: un teléfono es el **host** (pone la música) y los demás se unen por la
misma red Wi-Fi para adivinar la canción entre 4 opciones. Gana quien sume más puntos.

## Cómo jugar
1. Instala `AdivinaLaCancion.apk` en todos los teléfonos (permitir "orígenes desconocidos").
2. Todos en la misma Wi-Fi (o conectados al hotspot del host).
3. El host toca **Crear sala**, elige un género del top de Deezer o busca un artista/playlist, y
   configura rondas y tiempo.
4. Los demás tocan **Unirme a una sala**. La sala aparece sola; si no, escriben la IP que muestra el host.
5. Cada ronda suena un preview de 30 s en el teléfono del host. Si aciertas ganas de 100 a 1000 puntos,
   según qué tan rápido respondas.

## Técnico
- Kotlin + Jetpack Compose, minSdk 26.
- Música: API pública de Deezer (sin login), con previews de 30 s.
- Red: TCP en el puerto 47778, con JSON de una línea por mensaje. El descubrimiento de salas es por
  broadcast UDP en el puerto 47777. El host es la fuente de verdad y también se conecta a sí mismo como jugador.
- El núcleo está en `game/` (reglas puras) y `net/` (servidor, cliente y protocolo).

```bash
./gradlew testDebugUnitTest   # 52 tests, incluida una partida completa por TCP real
./gradlew assembleRelease     # APK en app/build/outputs/apk/release/
```
El release está firmado con la llave debug: sirve para instalarlo directo, no para publicarlo en Play Store.
