# SnipShot Android – Feature Implementation Plan

Based on the desktop version (`snipshot-desktop`) which has:
- 📸 Screen Capture (overlay snipping)
- 🌐 Translation (manga/comic OCR → translate → overlay)
- ☁️ Cloud Storage (Supabase backend: save images per user)
- 📁 Folder Organisation (Google Drive–style)
- 🔐 Auth (register / login / logout with JWT)
- ⚙️ Advanced Settings (target language, inpainter, detection size, box threshold, etc.)

The Android app currently only has the **Snip + basic OCR translate** pipeline. The missing features are:

---

## Current State of Android App

| Component | Status |
|---|---|
| Floating bubble launcher (`BubbleService`) | ✅ Done |
| Screen capture (`SnipActivity` + `ScreenCaptureService`) | ✅ Done |
| Snip selection overlay (`SnipOverlayActivity`) | ✅ Done |
| OCR + Text translation (`performOcrAndTranslate`) | ✅ Done |
| Translation result overlay (`OverlayActivity`) | ✅ Done |
| Text-only result view (`TranslateActivity`) | ✅ Done |
| Basic settings – target language only (`SettingsActivity`) | ✅ Done (basic) |
| **User Auth (Register / Login / Logout)** | ❌ Missing |
| **Cloud save – upload translated image to backend** | ❌ Missing |
| **Folder organisation (create, rename, delete, browse)** | ❌ Missing |
| **Dashboard / My Files screen** | ❌ Missing |
| **Translation mode toggle** (manga_translator vs. simple OCR) | ❌ Missing |
| **Advanced translation settings** (inpainter, detection size, threshold) | ❌ Missing |
| **Image library (view saved cloud images)** | ❌ Missing |

---

## Feature 1 – User Authentication

### What the desktop does
- `ui/login.py` and `ui/register.py` present email/password forms.
- `api/client.py` calls `POST /api/users/login` and `POST /api/users/register`.
- JWT tokens (`access_token`, `refresh_token`) are stored in memory; the Authorization header is sent on every subsequent request.
- Logout clears the tokens from memory.

### Android plan

#### 1.1 – `ApiClient.kt` (new singleton)
Create `app/src/main/java/com/example/snipshot/api/ApiClient.kt`:
- Wraps OkHttp (already a dependency).
- Stores `accessToken`, `refreshToken`, and `user` in memory (+ `SharedPreferences` for persistence across app restarts).
- Exposes suspend functions:
  - `register(email, password)` → `POST /api/users/register`
  - `login(email, password)` → `POST /api/users/login`
  - `logout()` → clears tokens
  - `getProfile()` → `GET /api/users/me`
- On every authed call, injects `Authorization: Bearer <token>` header.
- Base URL sourced from `BuildConfig.BACKEND_URL` (already wired via `.env`).

> **Note:** The existing `SnipOverlayActivity` uses `BuildConfig.BACKEND_URL` for a *different* base URL (OCR/translate API). Auth calls go to the **Database API** URL. A second `BuildConfig` field `DATABASE_API_URL` should be added to `.env` and `build.gradle.kts`.

#### 1.2 – `LoginActivity.kt` (new)
- Material 3 UI: email `TextInputEditText`, password `TextInputEditText` (with show/hide toggle), "Login" button, "Don't have an account? Register" link.
- On submit: call `ApiClient.login()` in a coroutine; on success navigate to `DashboardActivity`; on error show a `Snackbar`.
- Persist tokens to `SharedPreferences` on success.

#### 1.3 – `RegisterActivity.kt` (new)
- Same UI pattern as Login, plus a confirm-password field.
- Calls `ApiClient.register()`.

#### 1.4 – `SplashActivity.kt` (new) or modify `MainActivity`
- On launch, check `SharedPreferences` for a stored `access_token`.
- If found → navigate to `DashboardActivity` (skip login).
- If not → navigate to `LoginActivity`.
- Replace the current `MainActivity` (which just requests overlay permission and starts the bubble service) with a flow that requests the overlay permission *after* login if not already granted.

#### 1.5 – Logout
- `DashboardActivity` toolbar menu item "Sign Out" → calls `ApiClient.logout()`, clears `SharedPreferences`, navigates back to `LoginActivity`, stops `BubbleService` if running.

---

## Feature 2 – Cloud Save (Upload Translated Image)

### What the desktop does
- After translation, `translation.py` lets the user click **Save**.
- `api/client.py::save_image_from_bytes()` calls `POST /api/images/upload` as a multipart form with the image bytes, optional `folder_id`, `source_language`, and `target_language`.
- Returns the saved image record (filename, public URL, etc.).

### Android plan

#### 2.1 – `ApiClient.kt` additions
- `uploadImage(imageBytes, filename, folderId?, sourceLang?, targetLang?)` → `POST /api/images/upload` (multipart).
- `getFolders()` → `GET /api/folders`
- `getImages(folderId?)` → `GET /api/images`
- `deleteImage(imageId)` → `DELETE /api/images/{id}`

#### 2.2 – Save prompt in `OverlayActivity`
After translation finishes and the overlay is shown, add a **"Save to Cloud"** `FloatingActionButton` or bottom sheet:
1. If not logged in → show "You must log in to save" toast.
2. If logged in, show a bottom sheet (`FolderPickerBottomSheet`) listing the user's folders + an "Unsorted" option.
3. On selection: call `ApiClient.uploadImage()` in a coroutine with a progress indicator.
4. On success: show a `Snackbar` with "Saved to [folder name]".

#### 2.3 – `FolderPickerBottomSheet.kt` (new)
- `BottomSheetDialogFragment` that loads folders from `ApiClient.getFolders()` and presents them in a `RecyclerView`.
- Includes a "No folder (Unsorted)" option at the top.
- Emits a callback `onFolderSelected(folderId: Int?)`.

---

## Feature 3 – Dashboard / My Files Screen

### What the desktop does
- `DashboardWindow` (PyQt5) has a sidebar with:
  - **My Files** (folders grid + unfiled images grid)
  - **Recent** (images sorted by date)
  - **Settings**
- Folder cards show name and image count; right-click → open / rename / delete.
- Image cards show a thumbnail placeholder, filename, and file size; right-click → view / rename / move / delete.
- Full-size image preview dialog with scrollable image, "Open in Browser" button.

### Android plan

#### 3.1 – `DashboardActivity.kt` (new)
- Uses `BottomNavigationView` or a `NavigationDrawer` with three sections:
  - **My Files** (default)
  - **Recent**
  - **Settings** (navigates to `SettingsActivity`)
- Top `Toolbar` shows "SnipShot" title, a refresh icon, and a user email / sign-out overflow menu.
- Hosts a `Fragment` container; swaps between `MyFilesFragment`, `RecentFragment`.

#### 3.2 – `MyFilesFragment.kt` (new)
- Loads folders (`ApiClient.getFolders()`) and unfiled images (`ApiClient.getImages(folderId = null)`).
- Renders folders in a `RecyclerView` with `GridLayoutManager` (2 columns on phones, 3+ on tablets).
- Each `FolderViewHolder` shows 📁 icon, folder name, image count.
- Long-press on a folder → `PopupMenu` with Rename / Delete.
- Tap on a folder → opens `FolderDetailFragment`.
- Below folders, shows unfiled images in the same grid style.
- FAB "New Folder" at bottom-right.

#### 3.3 – `FolderDetailFragment.kt` (new)
- Shows images inside a selected folder.
- Back arrow → returns to `MyFilesFragment`.
- Long-press image → PopupMenu: View / Move to Folder / Delete.
- FAB is hidden (or repurposed to "Take New Snip").

#### 3.4 – `RecentFragment.kt` (new)
- Loads all images sorted by `created_at` desc (backed by `ApiClient.getImages()`).
- Same grid view as `FolderDetailFragment`.

#### 3.5 – `ImageDetailActivity.kt` (new)
- Full-screen `PhotoView` (or `ZoomableImageView`) of the cloud image.
- Loads image from `public_url` using Coil or Glide.
- Bottom bar: filename label + Delete button + "Open in Browser" icon.

#### 3.6 – `ApiClient.kt` additions for folders
- `createFolder(name, description)` → `POST /api/folders`
- `renameFolder(id, name)` → `PUT /api/folders/{id}`
- `deleteFolder(id)` → `DELETE /api/folders/{id}`
- `moveImage(imageId, folderId)` → `PUT /api/images/{id}` with `{ folder_id }`

---

## Feature 4 – Translation Modes & Advanced Settings

### Overview
The Android app will support **two translation modes** that the user can switch between in Settings. Each mode uses a different backend endpoint and produces a different type of result.

---

### Mode 1 – Manga Translator (Desktop-parity, rendered image)

**Endpoint:** `POST /translate/raw` (same service as desktop)

**Request shape** (mirrors the desktop Python call exactly):
```
multipart/form-data
  image  : <PNG/JPEG bytes>
  config : {"translator": {"target_lang": "ENG"},
             "detector":  {"detection_size": 1536, "box_threshold": 0.7},
             "inpainter": {"inpainter": "lama", "inpainting_size": 2048}}
```

**Response:** Raw image bytes (PNG) — the backend returns a fully rendered, text-replaced image.

**Result display:** Show the returned PNG in a full-screen `ImageView` in `MangaResultActivity`. A "Save to Cloud" FAB is shown below the image.

**Supports advanced config** — detection size, box threshold, inpainting size, and inpainter all take effect.

**Supported target language codes:**
| Display | Code |
|---|---|
| English | `ENG` |
| Japanese | `JPN` |
| Korean | `KOR` |
| Chinese (Simplified) | `CHS` |
| Chinese (Traditional) | `CHT` |

---

### Mode 2 – Simple OCR + Translate (Current Android pipeline)

**Endpoints (sequential):**
1. `POST /ocr` with `{ "image_base64": "<base64>" }` → returns `items[]` with `text` + `bbox`
2. `POST /translate` per item with `{ "text": "...", "target_lang": "en" }` → returns `translated_text`

**Response:** Text strings + bounding boxes → drawn as translucent `TextView` overlays on the original cropped bitmap in `OverlayActivity` (existing behaviour, unchanged).

**Does not support** advanced config (inpainter, detection size, etc.).

**Supported target language codes:**
| Display | Code |
|---|---|
| English | `en` |
| Japanese | `ja` |
| Korean | `ko` |
| Chinese (Simplified) | `zh_cn` |
| Chinese (Traditional) | `zh_tw` |

---

### Android plan

#### 4.1 – `TranslationMode.kt` (new)
```kotlin
enum class TranslationMode { MANGA_TRANSLATOR, SIMPLE_OCR }
```
Stored in `SharedPreferences` as `translation_mode`.

#### 4.2 – Refactor `SnipOverlayActivity`
Replace the single `performOcrAndTranslate()` call with a dispatcher:
```kotlin
when (translationMode) {
    MANGA_TRANSLATOR -> performMangaTranslation(bitmap)
    SIMPLE_OCR       -> performOcrAndTranslate(bitmap)   // existing logic, untouched
}
```

**`performMangaTranslation(bitmap)`** (new method):
1. Compress bitmap to PNG bytes.
2. Read `target_lang_manga`, `detection_size`, `box_threshold`, `inpainting_size`, `inpainter` from `SharedPreferences`.
3. Build `config` JSON:
   ```json
   {
     "translator": { "target_lang": "ENG" },
     "detector":   { "detection_size": 1536, "box_threshold": 0.7 },
     "inpainter":  { "inpainter": "lama", "inpainting_size": 2048 }
   }
   ```
4. POST to `BuildConfig.BACKEND_URL + "/translate/raw"` as `multipart/form-data` with fields `image` (PNG bytes) and `config` (JSON string).
5. On HTTP 200 → write response bytes to a temp cache file and start `MangaResultActivity`.
6. On error → show `Snackbar` with error message and HTTP status.

#### 4.3 – `MangaResultActivity.kt` (new)
Displayed when Mode 1 returns successfully:
- Full-screen `ImageView` with pinch-to-zoom (via `PhotoView` library or manual `Matrix` scaling).
- Bottom bar with:
  - Filename / timestamp label
  - **"Save to Cloud"** button → opens `FolderPickerBottomSheet`
  - **Share** icon → `ACTION_SEND` intent with image bytes
- Image loaded from a temp cache file path passed via Intent extra (avoids `TransactionTooLargeException`).

#### 4.4 – Expand `SettingsActivity`
Replace the single-language spinner with a structured settings screen:

**Translation Mode section:**
- `RadioGroup` with two options:
  - 🖼️ **Manga Translator** — *full inpainting, returns rendered image*
  - 📝 **Simple OCR + Translate** — *text overlays, faster*
- Selecting Simple OCR collapses/disables the Advanced section.

**Translation section:**
- Target Language `Spinner` (list adapts to the selected mode's language codes)
- Source Language `Spinner` (auto-detect default; Mode 1 only)

**Advanced section** (visible only when Mode 1 is selected):
- Inpainter `Spinner` (`lama` default / `mi-gan` / `manga` / `none`)
- Detection Size `SeekBar` (1024–4096 step 64; badge label shows current value)
- Box Threshold `SeekBar` (0.10–0.99 float; badge label shows 2 d.p.)
- Inpainting Size `SeekBar` (1024–4096 step 64; badge label shows current value)

**Save button** at the bottom — values only applied on Save (matches desktop pattern).

`SharedPreferences` keys:
| Key | Default |
|---|---|
| `translation_mode` | `MANGA_TRANSLATOR` |
| `target_lang_manga` | `ENG` |
| `target_language` (existing) | `en` |
| `inpainter` | `lama` |
| `detection_size` | `1536` |
| `box_threshold` | `0.7` |
| `inpainting_size` | `2048` |

---

## Implementation Order (Suggested)

```
Phase 1 – Foundation
  1. Add DATABASE_API_URL to .env + build.gradle.kts
  2. Create ApiClient.kt (auth + image + folder endpoints)
  3. Modify app startup flow (SplashActivity / MainActivity)
  4. LoginActivity + RegisterActivity

Phase 2 – Cloud Save
  5. FolderPickerBottomSheet
  6. "Save to Cloud" button in OverlayActivity
  7. ApiClient: uploadImage, getFolders, getImages

Phase 3 – Dashboard
  8. DashboardActivity (shell + nav)
  9. MyFilesFragment + FolderDetailFragment
  10. RecentFragment
  11. ImageDetailActivity
  12. ApiClient: createFolder, renameFolder, deleteFolder, moveImage

Phase 4 – Translation Modes & Advanced Settings
  13. Add TranslationMode enum
  14. Refactor SnipOverlayActivity into mode dispatcher
  15. Implement performMangaTranslation() (Mode 1)
  16. Create MangaResultActivity
  17. Expand SettingsActivity UI (mode toggle + advanced section)
```

---

## New Files Summary

| File | Purpose |
|---|---|
| `api/ApiClient.kt` | Singleton HTTP client; auth, folders, images |
| `SplashActivity.kt` | Auth gate on startup |
| `LoginActivity.kt` | Email/password login screen |
| `RegisterActivity.kt` | Registration screen |
| `DashboardActivity.kt` | Main post-login screen with bottom nav |
| `MyFilesFragment.kt` | Folders + unfiled images grid |
| `FolderDetailFragment.kt` | Images inside a folder |
| `RecentFragment.kt` | All images sorted by date |
| `FolderPickerBottomSheet.kt` | Folder selection for cloud save |
| `ImageDetailActivity.kt` | Full-screen viewer for cloud images |
| `TranslationMode.kt` | Enum: `MANGA_TRANSLATOR` / `SIMPLE_OCR` |
| `MangaResultActivity.kt` | Displays rendered image returned by `/translate/raw` |
| `res/layout/activity_login.xml` | Login UI layout |
| `res/layout/activity_register.xml` | Register UI layout |
| `res/layout/activity_dashboard.xml` | Dashboard shell layout |
| `res/layout/fragment_my_files.xml` | My Files grid layout |
| `res/layout/fragment_recent.xml` | Recent images layout |
| `res/layout/item_folder.xml` | Folder card for RecyclerView |
| `res/layout/item_image.xml` | Image card for RecyclerView |
| `res/layout/bottom_sheet_folder_picker.xml` | Folder picker bottom sheet |
| `res/layout/activity_image_detail.xml` | Full-screen image viewer |
| `res/layout/activity_manga_result.xml` | Manga Translator result screen |

## Modified Files

| File | Change |
|---|---|
| `AndroidManifest.xml` | Add new activities; set `SplashActivity` as launcher |
| `SettingsActivity.kt` | Add mode toggle (RadioGroup) + advanced settings UI |
| `OverlayActivity.kt` | Add "Save to Cloud" FAB / button (Mode 2 result screen) |
| `SnipOverlayActivity.kt` | Refactor into mode dispatcher; add `performMangaTranslation()` |
| `app/build.gradle.kts` | Add `DATABASE_API_URL` BuildConfig field; add Coil/Glide + PhotoView deps |
| `.env` | Add `DATABASE_API_URL` |

---

## Open Questions

1. **Token persistence**: Should `access_token` be stored in `SharedPreferences` (simple) or `EncryptedSharedPreferences` (recommended for production)?

2. **Image loading library**: Coil (Kotlin-idiomatic) or Glide (more established)? Both support URL loading and disk caching.

3. **Min SDK**: Current `minSdk = 26`. Auth + cloud features don't require raising this.

4. **Bubble entry point after login**: Should the floating bubble remain the primary entry point, or should the app open `DashboardActivity` on launch (like the desktop) with the bubble as an optional overlay shortcut?

5. **Default translation mode**: Should **Manga Translator** (Mode 1) or **Simple OCR** (Mode 2) be the default on first install? Mode 1 is higher quality but slower and requires the manga_translator service to be running. Mode 2 is faster with a simpler backend.
