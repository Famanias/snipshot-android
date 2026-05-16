# SnipShot Android Implementation Documentation

## 1. Overview
This document outlines the changes made to bring the SnipShot Android app to feature parity with its desktop counterpart. The implementation tightly follows the specifications detailed in `plan.md`, covering offline-first functionality, cloud synchronization via authentication, a revamped dashboard UI, and a dual-mode translation system (Manga Translator vs. Simple OCR).

## 2. Architecture Changes
- **Application Class Initialization**: Introduced `SnipShotApp` extending `Application` to initialize singletons (like `ApiClient`) on startup.
- **ActivityLifecycleCallbacks**: `SnipShotApp` implements `ActivityLifecycleCallbacks` to globally track the app's foreground/background state, decoupling the floating bubble logic from individual activities.
- **Singleton API Client**: Encapsulated all network and authentication logic in an `ApiClient` object to keep UI components lean and decoupled from HTTP details.
- **Unified File Adapter Pattern**: Introduced `FileItemAdapter` with a sealed `FileItem` class to handle displaying both local `java.io.File` objects and remote JSON cloud structures in a single `RecyclerView`.

## 3. Features Implemented

- **App Startup Flow**: `SplashActivity` now serves as the entry point, resolving routing directly to `DashboardActivity`. The app operates offline by default, and no forced login gate exists.
- **Dashboard UI**: `DashboardActivity` manages a `BottomNavigationView` with three sections: My Files, Recent, and Settings.
- **Authentication**: `LoginActivity` and `RegisterActivity` allow users to authenticate. JWT tokens are securely stored in `EncryptedSharedPreferences`.
- **Local Storage Mode**: Using `StorageManager`, images are saved to the app's external files directory (`getExternalFilesDir("Translated")`) when the user is not authenticated.
- **Cloud Save & Folder Management**: Authenticated users are presented with a `FolderPickerBottomSheet` upon saving images, letting them upload files to specific cloud folders.
- **Translation Modes**: 
  - **Mode 1 (Manga Translator)**: Advanced pipeline using the `/translate/raw` endpoint, returning fully rendered images displayed in `MangaResultActivity`.
  - **Mode 2 (Simple OCR)**: The legacy pipeline using `/ocr` + `/translate`, displaying text boxes over the original image in `OverlayActivity`.
- **System Overlay Bubble**: The floating bubble now only activates when the app enters the background, behaving like a chat-head system overlay.

## 4. File Changes

### New Files Created
- `app/src/main/java/.../SnipShotApp.kt`: Application base class for lifecycle tracking and initialization.
- `app/src/main/java/.../api/ApiClient.kt`: Handles auth, token persistence, and backend API calls.
- `app/src/main/java/.../utils/StorageManager.kt`: Helper object for reading and writing to `getExternalFilesDir`.
- `app/src/main/java/.../TranslationMode.kt`: Enum defining `MODE_1_MANGA` and `MODE_2_SIMPLE_OCR`.
- **Activities & Fragments**: `SplashActivity.kt`, `DashboardActivity.kt`, `LoginActivity.kt`, `RegisterActivity.kt`, `MangaResultActivity.kt`, `ImageDetailActivity.kt`, `MyFilesFragment.kt`, `RecentFragment.kt`, `FolderDetailFragment.kt`.
- **UI Adapters & Dialogs**: `FileItemAdapter.kt`, `FolderPickerBottomSheet.kt`.
- **Layouts**: `activity_splash.xml`, `activity_dashboard.xml`, `activity_login.xml`, `activity_register.xml`, `activity_manga_result.xml`, `activity_image_detail.xml`, `fragment_my_files.xml`, `fragment_recent.xml`, `fragment_folder_detail.xml`, `item_folder.xml`, `item_image.xml`, `item_folder_picker.xml`, `bottom_sheet_folder_picker.xml`.
- **Menus & Drawables**: `bottom_nav_menu.xml`, `dashboard_menu.xml`, `bottom_sheet_bg.xml`.

### Modified Files
- `app/build.gradle.kts`: Added dependencies for Coil (image loading), Security Crypto (encrypted preferences), PhotoView (zoomable images), and `DATABASE_API_URL` to BuildConfig.
- `.env`: Added `DATABASE_API_URL`.
- `AndroidManifest.xml`: Registered `SnipShotApp` and all newly created Activities. Replaced `MainActivity` with `SplashActivity` as the default launcher.
- `SettingsActivity.kt` / `activity_settings.xml`: Expanded to include a `RadioGroup` for mode toggling and a dynamically visible layout for Mode 1 advanced configuration parameters.
- `SnipOverlayActivity.kt`: Renamed translation function to `performMode2OCR` and added `performMode1Manga`. Added a mode dispatcher within `captureSnip()`.
- `OverlayActivity.kt`: Migrated to `AppCompatActivity`, and added "Save" button logic integrating `StorageManager` and `FolderPickerBottomSheet`.

## 5. API & Backend Integration
The `ApiClient` is constructed using `OkHttp` and coroutines, pointing to `BuildConfig.DATABASE_API_URL`. 
**Endpoints mapped:**
- `POST /auth/login` and `POST /auth/register`: Retrieves a JWT token.
- `GET /folders`: Lists user folders.
- `GET /images`: Retrieves all images or unfiled images (supports `folder_id` query param).
- `POST /images`: Multipart upload for images to the cloud database.
- `DELETE /images/{id}`: Deletes a specific cloud image.
**Token Persistence:** Uses `EncryptedSharedPreferences`. On launch, `init()` checks for a valid token and securely attaches it to the `Authorization: Bearer` header for protected endpoints.

## 6. Local vs Cloud Behavior
- **Local Mode (Logged Out)**: The Dashboard (`MyFilesFragment` and `RecentFragment`) utilizes `StorageManager` to query local device files. Saving from `OverlayActivity` or `MangaResultActivity` immediately writes a `.png` file to the device.
- **Cloud Mode (Logged In)**: Dashboard fragments swap to calling `ApiClient.getFolders()` and `ApiClient.getImages()`. When saving a snip, the app prompts a `FolderPickerBottomSheet`, fetching the authenticated user's folders and uploading the image bytes to the backend.

## 7. Key Technical Decisions
- **Sealed Class Adapter**: `FileItemAdapter` uses a `sealed class FileItem` to handle both `LocalImage`, `CloudImage`, and `Folder`. This abstraction means the UI layer doesn't need to write separate `RecyclerView.Adapter` classes for local vs. cloud states.
- **Lifecycle Overlay Management**: Rather than binding the bubble service start/stop to `onPause`/`onResume` of individual activities (which can flicker during transitions), `Application.ActivityLifecycleCallbacks` increments a reference counter on start and decrements on stop. A count of `0` confidently indicates the entire app has moved to the background.
- **Coil vs. Glide**: Used Coil because of its first-class Kotlin Coroutines support and modern, lightweight footprint compared to Glide.

## 8. Known Limitations / Future Improvements
- **Pagination**: The `getImages()` API endpoint is currently loading the first page up to 50 items. Infinite scrolling with `RecyclerView` pagination logic needs to be implemented for large cloud libraries.
- **Token Refresh**: The `ApiClient` assumes the JWT token is valid if it exists. Token expiration interception (`401 Unauthorized`) and silent refreshing via a refresh token flow is not fully implemented.
- **Cloud Syncing**: Local files captured offline do not automatically sync to the cloud when the user logs in. A manual "Upload to Cloud" option from the local detail view could bridge this gap.

## 9. Setup / Run Instructions
- **Environment Setup**: Ensure `.env` contains `DATABASE_API_URL` pointing to the authentication/database backend (e.g., `http://10.0.2.2:8001` for emulators). The existing `BACKEND_URL` is still required for the OCR/Translation workers (`http://10.0.2.2:8002`).
- **Dependencies**: The project requires an active internet connection to download `io.coil-kt:coil`, `androidx.security:security-crypto`, and `com.github.chrisbanes:PhotoView` from Maven/JitPack.
- **Build**: No special build configurations are required beyond a standard Gradle Sync and running `compileDebugKotlin`.
