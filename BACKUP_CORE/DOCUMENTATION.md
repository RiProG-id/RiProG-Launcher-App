# BACKUP_CORE Documentation

This folder contains core reusable features and designs extracted from the RiProG Launcher project. These modules are organized for easy cherry-picking into other repositories.

## Modules

### 1. Theme System (`/ThemeSystem`)
- **Purpose:** Entire theme switching mechanism and "Liquid Glass" design.
- **Contents:**
  - `ThemeUtils.kt`: Utility for generating glass drawables and adaptive colors.
  - `ThemeMechanism.kt`: Logic for applying theme modes and extracting system accent colors.
  - `colors.xml`, `colors-night.xml`: Core color palettes.
- **Required Dependencies:** AndroidX Core, Material Components.
- **Integration Guide:** Copy `ThemeUtils.kt` and `ThemeMechanism.kt` to your project. Ensure the colors are merged into your `res/values/colors.xml`.

### 2. Auto Dimming Background (`/AutoDimming`)
- **Purpose:** Subtle background dimming in dark mode.
- **Contents:**
  - `AutoDimmingBackground.kt`: Handles the creation and visibility logic of the dim overlay.
- **Required Dependencies:** `LauncherPreferences` (or equivalent settings provider).
- **Integration Guide:** Initialize `AutoDimmingBackground` with your main layout. Call `updateDimVisibility()` when the theme or user preferences change.

### 3. Freeform Edit Mode (`/FreeformEdit`)
- **Purpose:** Interaction controls for resizing, rotating, and moving items without grid constraints.
- **Contents:**
  - `TransformOverlay.kt`: The main UI overlay with handles for interaction.
  - `FreeformInteraction.kt`: Helper for managing the overlay lifecycle.
- **Required Dependencies:** `ThemeUtils`, `LauncherPreferences`, `HomeItem` model.
- **Integration Guide:** Add `TransformOverlay` to your root layout when an item is long-pressed in freeform mode.

### 4. Folder System (`/FolderSystem`)
- **Purpose:** Logic and UI for grouping items into folders.
- **Contents:**
  - `FolderManager.kt`: Business logic for folder operations (open, close, merge).
  - `FolderUI.kt`: Components for rendering folder previews and views.
- **Required Dependencies:** `MainActivity` (as a callback), `HomeItem`.
- **Integration Guide:** Use `FolderManager` to handle drag-and-drop collisions and folder opening.

### 5. Page System (`/PageSystem`)
- **Purpose:** Visual indicators and menu design for multi-page layouts.
- **Contents:**
  - `PageIndicator.kt`: Animated dot indicator for current page.
  - `PageMenuUI.kt`: Logic for showing the Add/Remove page dialog.
- **Integration Guide:** Integrate `PageIndicator` with your horizontal pager. Use `PageMenuUI` to provide user controls for page management.

### 6. Widget Picker (`/WidgetPicker`)
- **Purpose:** Full-screen UI for selecting and dragging system widgets.
- **Contents:**
  - `WidgetManager.kt`: Implementation of the widget selection dialog.
- **Required Dependencies:** `AppWidgetManager`.
- **Integration Guide:** Call `pickWidget()` to show the selection UI.

### 7. Settings Design (`/SettingsDesign`)
- **Purpose:** Clean, glass-themed settings UI.
- **Contents:**
  - `SettingsActivity.kt`: Implementation of the settings screen layout.
- **Required Dependencies:** Koin (for dependency injection), AndroidX Activity/Lifecycle, `HomeViewModel`.
- **Integration Guide:** Use as a template for your own settings activity. You will need to provide a `HomeViewModel` and set up Koin modules or refactor to use your preferred DI/ViewModel approach.

### 8. Design Assets (`/DesignAssets`)
- **Purpose:** Reusable visual resources and supporting text.
- **Contents:**
  - `drawables/`: Vector icons for various launcher actions.
  - `values/`: Colors, dimensions, and `strings.xml`.
- **Integration Guide:** Merge these resources into your project's `res` directory. The `strings.xml` contains all the labels used by the backed-up modules.

## General Integration Notes
- **Data Models:** Most modules depend on the `HomeItem` class for data modeling.
- **Preferences:** A `LauncherPreferences` class is expected for managing user settings (like `isLiquidGlass`).
- **Dependency Injection:** The original project uses Koin. If you don't use Koin, you'll need to refactor the `@inject` and `@viewModel` delegates.
- **Activity Context:** Many UI managers (like `FolderManager`) currently take `MainActivity` as a parameter. For better modularity, you should consider defining an interface for the required callbacks.
