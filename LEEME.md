# EmuDroid: emulador de Android para Windows

Programa propio (Kotlin + Compose for Desktop) para crear, encender y controlar teléfonos Android virtuales.
Por dentro usa el motor gratuito de Google (Android Emulator), el mismo que usa Android Studio.

## Qué hace
- **Mis teléfonos**: lista de teléfonos virtuales con su estado (Apagado / Iniciando / Encendido), botones Encender, Apagar, Arranque en frío, Borrar datos y Eliminar.
- **Panel de control** del teléfono encendido: Instalar APK, Captura de pantalla (se guarda en `Imágenes\EmuDroid`), Rotar, Atrás, Inicio, Recientes, Encendido, Volumen + y −, Silencio, escribir texto y abrir enlaces.
- **Arrastrar y soltar** un archivo `.apk` en cualquier parte de la ventana para instalarlo.
- **Crear teléfono**: nombre, modelo (Pixel 7, Pixel 6, tablet…), versión de Android y RAM (2, 3, 4 o 6 GB). Activa el teclado de la PC dentro del teléfono.
- **Descargar Android**: descarga Android 11 a 16 con Play Store, con barra de progreso.
- **Ajustes y ayuda**: detecta solo la carpeta del SDK, revisa qué componentes faltan y explica los pasos.
- **Registro** abajo, donde se ve todo lo que va pasando.

## Preparar la PC (una sola vez)
1. Instale **Android Studio**: https://developer.android.com/studio
2. En Android Studio: *More Actions → SDK Manager → SDK Tools* y marque **Android Emulator**, **Android SDK Platform-Tools** y **Android SDK Command-line Tools (latest)**. Pulse Apply.
3. En Windows: "Activar o desactivar las características de Windows" → marque **Plataforma del hipervisor de Windows** y reinicie. Si aun así no arranca, active VT-x / AMD-V (SVM) en la BIOS.

## Abrir y ejecutar el proyecto
1. Abra la carpeta `EmuDroid` en **IntelliJ IDEA** (o Android Studio con el plugin de Kotlin Multiplatform).
2. Espere a que Gradle descargue todo (la primera vez tarda unos minutos).
3. En el panel **Gradle** abra: `EmuDroid → Tasks → compose desktop → run`.

## Generar el instalador para Windows
En el panel Gradle: `compose desktop → packageMsi` (o `packageExe`).
El instalador queda en `build\compose\binaries\main\msi\`.

> Si quiere usar `gradlew` en la consola y tiene Gradle instalado, ejecute primero `gradle wrapper` dentro de la carpeta.

## Archivos
- `Main.kt`: ventana principal y función de arrastrar y soltar.
- `AppState.kt`: estado del programa y todas las acciones.
- `Sdk.kt`: comunicación con emulator, adb, avdmanager y sdkmanager.
- `Screens.kt`: todas las pantallas (diseño oscuro con verde Android).

La configuración se guarda en `%USERPROFILE%\.emudroid\config.properties`.
