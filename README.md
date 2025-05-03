# 🎮 DDR Performance Tracker

An Android app that scans Dance Dance Revolution score screens via on-device OCR and computer vision to automate stat logging, compare scores with other users, and track personal progression over time.

## Features

- 📸 **Score Screen Scanning** — Use your camera to scan DDR score screens; ML Kit OCR extracts scores automatically
- 📊 **Progression Tracking** — Visualize your improvement across hundreds of gameplay sessions
- 🏆 **Leaderboards** — Compare scores with other users on the same songs
- 🗃️ **Persistent Storage** — All gameplay metrics synced to Firebase + PostgreSQL backend
- 🔍 **Session History** — Browse and filter past sessions by song, difficulty, or date

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Camera / OCR | CameraX, Google ML Kit |
| Local DB | Room |
| Remote DB | Firebase Firestore, PostgreSQL |
| Architecture | MVVM + Repository Pattern |
| DI | Hilt |

## Project Structure

```
app/
└── src/
    └── main/
        ├── kotlin/com/tehoubi/ddrtracker/
        │   ├── ui/
        │   │   ├── screens/        # Compose screens (Home, Scan, History, Leaderboard)
        │   │   └── components/     # Reusable UI components
        │   ├── data/
        │   │   ├── local/          # Room database, DAOs, entities
        │   │   └── remote/         # Firebase & API clients
        │   ├── domain/
        │   │   ├── model/          # Domain models (Score, Session, Song)
        │   │   └── repository/     # Repository interfaces
        │   └── util/               # OCR parsing, image processing helpers
        └── res/                    # Resources (layouts, drawables, strings)
```

## Getting Started

### Prerequisites

- Android Studio Hedgehog or later
- Android SDK 26+
- A Firebase project with Firestore enabled
- Google ML Kit dependency (included via Gradle)

### Setup

1. Clone the repo:
   ```bash
   git clone https://github.com/TehoUbi/ddr-performance-tracker.git
   cd ddr-performance-tracker
   ```

2. Add your `google-services.json` from your Firebase project into `app/`.

3. Copy `.env.example` to `.env` and fill in your PostgreSQL connection string (if using remote backend):
   ```
   DB_URL=postgresql://user:password@host:5432/ddrtracker
   ```

4. Build and run on a device or emulator:
   ```bash
   ./gradlew assembleDebug
   ```

## Usage

1. Open the app and tap **Scan Score**
2. Point your camera at a DDR result screen
3. The app will auto-detect and parse your score, grade, combo, and song info
4. Confirm or edit extracted data, then save
5. View your stats on the **Dashboard** and compare on the **Leaderboard**

## Architecture

The app follows **MVVM** with a clean Repository pattern:

```
UI (Compose) → ViewModel → Repository → [Local Room DB / Remote Firebase]
```

OCR results flow through a dedicated parsing pipeline in `util/` before being mapped to domain models.

## Contributing

Pull requests are welcome. Please open an issue first to discuss major changes.

## License

MIT
