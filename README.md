# IPTV Player para Xiaomi 15

App Android nativa (Kotlin + Jetpack Compose) para gestionar y reproducir listas M3U de IPTV con soporte Chromecast.

## Características

- **Reproducción IPTV** — HLS (m3u8), RTMP, RTSP via ExoPlayer/Media3
- **Chromecast** — Enviar a cualquier TV con Chromecast
- **Modo PiP** — Picture-in-Picture al salir de la app
- **Gestión M3U** — Múltiples listas, actualización automática
- **Explorador Ubuntu** — Navega tu servidor Ubuntu y descarga listas
- **EPG** — Guía de programación XMLTV
- **Favoritos** — Marca canales favoritos
- **Categorías** — Filtra por grupos del M3U
- **Búsqueda** — Busca canales por nombre
- **Vistos recientemente** — Acceso rápido

---

## Requisitos

- Android 8.0+ (API 26)
- Android Studio Hedgehog o superior
- JDK 17

---

## Cómo compilar

1. Clona/copia la carpeta `IPTVPlayer` en tu máquina
2. Abre **Android Studio** → *Open an existing project* → selecciona `IPTVPlayer`
3. Espera a que Gradle sincronice (descarga ~500MB de dependencias la primera vez)
4. Conecta tu Xiaomi 15 por USB con **Depuración USB** activada
5. Pulsa **Run ▶** o usa:
   ```
   ./gradlew assembleDebug
   ```
6. El APK estará en `app/build/outputs/apk/debug/app-debug.apk`

---

## Configurar servidor Ubuntu (para explorar tus listas)

En tu Ubuntu, ve a la carpeta con tus archivos M3U:

```bash
cd /ruta/a/tus/listas
python3 -m http.server 8080
```

Esto expone un servidor HTTP en el puerto 8080. En la app:

1. Ve a **Listas** → **Explorar servidor Ubuntu**
2. Introduce la IP de tu Ubuntu seguida del puerto: `192.168.1.X:8080`
3. Navega hasta el archivo `.m3u` y tócalo para importarlo

> **Tip:** Encuentra la IP de tu Ubuntu con `ip addr show` o `hostname -I`

### Alternativas a Python HTTP Server

**nginx** (más estable para uso permanente):
```bash
sudo apt install nginx
# Edita /etc/nginx/sites-available/default y añade autoindex on;
```

**Compartir carpeta Samba** también funciona si expones un directorio por HTTP.

---

## Usar Chromecast

1. Asegúrate de que el Xiaomi 15 y el Chromecast están en la **misma red WiFi**
2. Al reproducir un canal, aparecerá el ícono de Cast (🔊) en la esquina superior derecha
3. Toca el ícono y selecciona tu dispositivo Chromecast/TV

---

## Estructura del proyecto

```
app/src/main/java/com/marcosrava/iptvplayer/
├── data/
│   ├── model/          # Channel, Playlist, EpgProgram
│   ├── local/          # Room DB, DAOs
│   └── repository/     # PlaylistRepository
├── parser/
│   ├── M3UParser.kt    # Parser de listas IPTV
│   └── XmltvParser.kt  # Parser EPG (XMLTV)
├── network/
│   └── HttpFileBrowser.kt  # Navega servidor HTTP Ubuntu
├── cast/
│   └── CastOptionsProvider.kt  # Config Chromecast
├── di/
│   └── AppModule.kt    # Inyección de dependencias (Hilt)
├── ui/
│   ├── screens/        # HomeScreen, PlaylistsScreen, etc.
│   ├── player/         # PlayerActivity + ViewModel
│   ├── viewmodel/      # HomeViewModel, PlaylistsViewModel, etc.
│   ├── navigation/     # AppNavigation
│   └── theme/          # Colores y tema oscuro
└── MainActivity.kt
```

---

## Dependencias principales

| Biblioteca | Uso |
|-----------|-----|
| Media3 / ExoPlayer | Reproducción HLS, RTMP, RTSP |
| Cast Framework | Chromecast |
| Room | Base de datos local |
| Hilt | Inyección de dependencias |
| Jetpack Compose | UI |
| OkHttp + Jsoup | HTTP y scraping del servidor |
| Coil | Carga de logos de canales |

---

## Solución de problemas

**"Error al actualizar lista"**
- Verifica que la URL del M3U es accesible desde el móvil
- Si es HTTP (no HTTPS), ya está configurado en `network_security_config.xml`

**"No puedo conectar a Ubuntu"**
- Verifica que el servidor Python está corriendo: `python3 -m http.server 8080`
- Comprueba que el Xiaomi y el Ubuntu están en la misma red WiFi
- Desactiva el firewall temporalmente: `sudo ufw allow 8080`

**Chromecast no aparece**
- Ambos dispositivos deben estar en la misma red WiFi
- Reinicia el Chromecast si no aparece en la lista
