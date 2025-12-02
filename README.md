# 🎮 Juego de Memoria

<div align="center">

![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/kotlin-%237F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpack-compose&logoColor=white)

**Un juego clásico de memoria desarrollado con Jetpack Compose para Android**

[Características](#-características) • [Tecnologías](#-tecnologías-utilizadas) • [Instalación](#-instalación) • [Uso](#-cómo-jugar)

</div>

---

## 📖 Descripción

Juego de Memoria es una aplicación Android desarrollada completamente en **Kotlin** utilizando **Jetpack Compose** y **Material Design 3**. El juego desafía a los usuarios a emparejar tarjetas con símbolos idénticos, con múltiples niveles de dificultad progresiva y dos modos de juego (normal y relax).

### 🎯 Objetivo del Juego

El objetivo es simple pero desafiante: **encontrar todas las parejas de tarjetas** volteándolas de dos en dos. Cuando dos tarjetas coinciden, quedan emparejadas permanentemente. El juego termina cuando todas las tarjetas han sido emparejadas.

---

## ✨ Características

### 🎮 Modos de Juego

- **Modo Normal**: Juego con restricciones de tiempo y límite de movimientos
- **Modo Relax**: Juego sin límites de tiempo ni movimientos para disfrutar sin presión

### 📈 Niveles de Dificultad

El juego cuenta con **5 niveles progresivos** que aumentan en dificultad:

| Nivel | Grid | Tarjetas | Movimientos Máx | Tiempo Límite |
|-------|------|----------|-----------------|---------------|
| 1 | 2×2 | 4 | 5 | 15 seg |
| 2 | 2×3 | 6 | 8 | 25 seg |
| 3 | 2×4 | 8 | 12 | 40 seg |
| 4 | 3×4 | 12 | 20 | 60 seg |
| 5 | 4×4 | 16 | 30 | 90 seg |

### 🎨 Características Técnicas

- ✅ **Soporte para Modo Claro y Oscuro**: Se adapta automáticamente al tema del sistema
- ✅ **Sistema de Sonidos**: Efectos de sonido para cada acción (voltear, acierto, error)
- ✅ **Animaciones Fluidas**: Transiciones suaves y animaciones de desvanecimiento
- ✅ **Navegación Intuitiva**: Sistema de navegación tipo-seguro usando Sealed Classes
- ✅ **Cronómetro en Tiempo Real**: Cuenta regresiva visual para el modo normal
- ✅ **Transición Automática**: Avance automático al siguiente nivel al completar uno
- ✅ **Diseño Moderno**: Interface atractiva con gradientes y Material Design 3

---

## 🛠️ Tecnologías Utilizadas

### Lenguaje y Framework

- **Kotlin**: Lenguaje principal del proyecto
- **Jetpack Compose**: Framework moderno de UI declarativa para Android
- **Material Design 3**: Sistema de diseño de Google

### Librerías y Componentes

- **Material 3 Components**: Componentes de UI modernos
- **SoundPool**: Sistema de reproducción de efectos de sonido
- **Coroutines**: Manejo asíncrono para timers y animaciones
- **State Management**: Gestión de estado reactivo con Compose State

### Arquitectura

- **Declarative UI**: Interfaz completamente declarativa con Compose
- **State-Driven**: Arquitectura basada en estados reactivos
- **Sealed Classes**: Sistema de navegación tipo-seguro

---

## 📋 Requisitos

- **Android SDK**: API nivel 24 (Android 7.0) o superior
- **Gradle**: 8.0 o superior
- **Android Studio**: Hedgehog (2023.1.1) o superior (recomendado)
- **JDK**: 17 o superior

---

## 🚀 Instalación

### Opción 1: Instalación Directa (APK)

1. Descarga el archivo APK desde la carpeta `app/release/`
2. Habilita la instalación desde fuentes desconocidas en tu dispositivo Android
3. Instala el APK descargado

### Opción 2: Compilación desde el Código Fuente

1. **Clona el repositorio:**
   ```bash
   git clone https://github.com/davicons/juegoMemoria.git
   cd JuegoMemoria
   ```

2. **Abre el proyecto en Android Studio:**
   - Abre Android Studio
   - Selecciona "Open an Existing Project"
   - Navega hasta la carpeta del proyecto y selecciónala

3. **Sincroniza el proyecto:**
   - Android Studio descargará automáticamente las dependencias necesarias
   - Espera a que finalice la sincronización de Gradle

4. **Conecta tu dispositivo o inicia un emulador:**
   - Conecta tu dispositivo Android vía USB y habilita la depuración USB
   - O crea/configura un emulador Android desde AVD Manager

5. **Ejecuta la aplicación:**
   - Haz clic en el botón "Run" (▶️) o presiona `Shift + F10`
   - Selecciona tu dispositivo o emulador
   - La app se compilará e instalará automáticamente

---

## 🎯 Cómo Jugar

### Inicio del Juego

1. Al abrir la aplicación, verás la **pantalla de bienvenida**
2. Selecciona uno de los dos modos:
   - **"¡Quiero jugar!"**: Modo normal con límites de tiempo y movimientos
   - **"Modo relax"**: Modo sin restricciones

### Durante el Juego

1. **Selecciona un nivel** del menú de niveles
2. **Voltea las tarjetas** tocándolas de dos en dos
3. **Encuentra las parejas**: Cuando dos tarjetas coinciden, quedan emparejadas
4. **Completa el nivel**: Empareja todas las tarjetas antes de que se acabe el tiempo (modo normal)

### Navegación

- **Botón Atrás** (←): Vuelve al menú anterior
- **Menú (⋮)**: Cambia rápidamente entre niveles
- **Reiniciar Nivel**: Reinicia el nivel actual sin perder tu progreso

### Consejos para Ganar

- 🧠 **Memoriza la posición** de las tarjetas que ya has visto
- ⏱️ **Gestiona tu tiempo** en modo normal
- 🎯 **Planifica tus movimientos** para minimizar intentos
- 🎮 **Usa el modo relax** para practicar sin presión

---

## 📁 Estructura del Proyecto

```
JuegoMemoria/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/juegomemoria/
│   │   │   │   ├── MainActivity.kt          # Activity principal y lógica del juego
│   │   │   │   └── ui/theme/                # Configuración de temas
│   │   │   ├── res/
│   │   │   │   ├── raw/                     # Archivos de audio (sonidos)
│   │   │   │   └── values/                  # Recursos (colores, estilos)
│   │   │   └── AndroidManifest.xml
│   │   └── test/                            # Tests unitarios
│   └── build.gradle.kts                     # Configuración del módulo
├── build.gradle.kts                         # Configuración del proyecto
├── gradle/                                  # Configuración de Gradle
└── README.md                                # Este archivo
```

### Componentes Principales

- **MainActivity.kt**: Contiene toda la lógica del juego, navegación y UI
  - Modelos de datos (GameScreen, Card, LevelData)
  - Sistema de sonidos (SoundPlayer)
  - Pantallas (WelcomeScreen, LevelSelectScreen, GameMemoryApp)
  - Componentes UI (CardView, ElectricBackground)

---

## 🎨 Características de Diseño

### Modo Claro y Oscuro

La aplicación se adapta automáticamente al tema del sistema:

- **Modo Oscuro**: Gradiente azul oscuro para proteger la vista
- **Modo Claro**: Gradiente azul claro y suave

Todos los textos y elementos UI utilizan colores del Material Theme, asegurando visibilidad óptima en ambos modos.

### Animaciones

- ✨ Desvanecimiento de tarjetas emparejadas
- 🎭 Transiciones suaves entre pantallas
- ⏱️ Actualización en tiempo real del cronómetro

---

## 🧪 Desarrollo y Contribución

### Estructura del Código

El código está completamente documentado con comentarios explicativos para facilitar el mantenimiento y la comprensión. Las secciones principales incluyen:

- **Modelos de Datos**: Sealed Classes para navegación tipo-segura
- **Gestión de Estado**: Uso de `remember()` y `LaunchedEffect` para estado reactivo
- **UI Declarativa**: Componentes Compose reutilizables y modulares

### Mejoras Futuras

Posibles mejoras para futuras versiones:

- [ ] Sistema de puntuación y ranking
- [ ] Más niveles adicionales
- [ ] Diferentes temáticas de tarjetas
- [ ] Modo multijugador
- [ ] Estadísticas y logros
- [ ] Personalización de sonidos

---

## 👨‍💻 Autor

**Ronald D. Condori**

Desarrollado como parte del curso de Aplicaciones Móviles II.

---

## 📄 Licencia

Este proyecto es de código abierto y está disponible para uso educativo y personal.

---

## 🙏 Agradecimientos

- Material Design 3 por los componentes de UI
- Jetpack Compose por el framework moderno de desarrollo
- La comunidad de Android por las herramientas y recursos

---

<div align="center">

**¡Disfruta del juego y desafía tu memoria! 🧠🎮**

⭐ Si te gustó el proyecto, considera darle una estrella ⭐

</div>

