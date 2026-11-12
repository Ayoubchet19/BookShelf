# BookShelf

BookShelf is a native Kotlin Android application for managing a personal library.
It supports books, categories, reading sessions, progress, favorites, completion,
search, filtering, sorting, and reading statistics.

## Architecture

The app uses a repository/ViewModel architecture:

```text
Activities -> BookListViewModel -> BookRepository -> Room DAOs -> SQLite
                    |                  |                 |
                 Kotlin Flow        Paging 3       Book / Category / ReadingSession
                    |
                Preferences DataStore
```

Room has three related entities. A book references a category and reading sessions
reference a book with cascading deletion. The repository validates all book and
session writes and imports `app/src/main/assets/books.json` only when the database
is empty. The bundled file contains 20 valid records and never overwrites user data.

## Screens

* Catalogue: Paging 3 `RecyclerView`, live search, favorites/completed filters.
* Book detail: progress, favorite toggle, delete, and reading-session recording.
* Add/edit book: validated CRUD form.
* Categories: category creation and reactive list.
* Statistics: completed books, favorites, pages, and reading minutes.
* Settings: persistent sort order, dark-mode preference, and completed-book visibility.

## Setup and validation

Open the root folder in Android Studio with API 35 and JDK 17, then run:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Instrumented tests can be run with an Android emulator:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

The local unit suite covers domain validation and progress behavior. Three Espresso
tests cover the catalogue search field, text entry, and category navigation. Run
them on an emulator with `connectedDebugAndroidTest` before the live demonstration.

## Debugging notes

* The original starter kept preferences in memory, so settings disappeared after
  process death. This was fixed by moving the three settings to Preferences DataStore
  and collecting them from the UI.
* The original repository seeded no persistent records. BookShelf now checks the
  Room count and performs a validated, transactional JSON import only once.

## Team contributions

Use the shared repository with one branch per contributor and integrate changes
through pull requests. Add each member's name and contribution before submission.
