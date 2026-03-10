# Changelog

## [3.0.1] - Post-Production Release

### New Features
- **Kotlin Migration**: Entire codebase successfully migrated to Kotlin for improved stability and performance.
- **App Folders**: Added support for creating and managing app folders on the home screen.
- **Acrylic UI Aesthetic**: Introduced a new "Acrylic" theme style featuring real-time blur and glass-morphism effects.
- **Dynamic Page Management**: Added the ability to add new pages to the left or right of the home screen and remove unwanted pages.
- **Advanced App Drawer**:
    - Added an alphabetical quick navigation bar (Index Bar) for faster app discovery.
    - Implemented a search feature with real-time filtering.
    - New long-press context menu for apps (Add to Home, App Info).
- **Home Menu Overlay**: Centralized hub for launcher actions (Widgets, Wallpaper, Settings, Page Management) accessible via long-press on empty home space.
- **Modernized Widget Picker**: Redesigned widget selection interface with per-app categorization.
- **Wallpaper Dimming**: New setting to automatically dim the wallpaper in Dark Mode for better readability.
- **Emergency Tools**: Added "Force Restart" and "Erase Data" options in Settings for troubleshooting.

### Improvements
- **Refined Home Screen Interaction**:
    - Improved freeform layout controller with better transformation handling (Rotation, Scale, Tilt).
    - Optimized grid snapping logic and overlap detection.
    - Added visual grid feedback during drag-and-drop operations.
- **Theme Management**:
    - Enhanced theme engine with better support for System, Light, and Dark modes.
    - Improved status bar contrast handling.
- **Performance & Stability**:
    - Implemented `AppRepository` for more efficient app list management.
    - Added automatic cleanup of home items when applications are uninstalled.
    - Optimized memory usage during app drawer transitions.
- **Project Infrastructure**:
    - Updated Build SDK to 36.
    - Migrated to Gradle Kotlin DSL (`build.gradle.kts`).
    - Standardized project structure into features, core, and data modules.

### Bug Fixes
- Fixed various stability issues when binding and resizing widgets.
- Resolved logic errors that allowed items to overlap unintentionally in non-freeform mode.
- Corrected issues where folder previews would not update correctly after adding or removing apps.
- Fixed app drawer scroll position reset when filtering apps.
