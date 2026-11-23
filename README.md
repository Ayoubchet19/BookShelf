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

## Reading sessions & statistics

### How it works

* **Book detail** (`BookDetailActivity` + `BookDetailViewModel`) shows the book's
  progress, its status (in progress / completed) and an estimated time left. It also
  has a form to record a session (pages, minutes) and a `RecyclerView` of the book's
  sessions (date, pages, minutes). A list with no sessions shows "No sessions yet".
  * **Edit** loads the session into the form, and the button becomes "Update session".
  * **Delete** asks for confirmation first.
  * Validation errors appear under the field concerned.
* **Statistics** (`StatisticsActivity` + `StatisticsViewModel`, `StateFlow`) shows:
  * completed books, favorites, pages read and total reading time;
  * average speed (pages/hour) and the reading streak;
  * a `RecyclerView` breakdown per category (books, pages, minutes).

  An empty state is shown while there is no session, favorite or completed book.
* All arithmetic lives in pure Kotlin functions in
  `app/src/main/java/fr/estiam/traillog/bookshelf/domain/ReadingStats.kt`:
  `computeProgress`, `validateSession`, `remainingPages`, `pagesPerHour`,
  `estimatedMinutesRemaining`, `readingStreak`, `categoryStats` and `buildStatistics`.
  The repository and the ViewModels only call them.
* Both screens keep their state in a ViewModel and collect it with
  `repeatOnLifecycle(STARTED)`, so a rotation loses nothing. This covers the session
  being edited, the validation errors and a pending delete confirmation (the dialog
  is shown again). Typed text is restored by the views themselves.

### Business rules

* A session needs an existing book, `pages > 0`, `minutes > 0` and
  `pages <= pages left in the book`. When a session is edited, its own previous pages
  count as available. A rejected session throws `SessionValidationException` and
  nothing is written.
* After each `addSession`, `updateSession` or `deleteSession`, one Room transaction
  (`db.withTransaction`) recomputes the book's progress from its sessions:
  * `book.pagesRead` = sum of the sessions' pages, capped to `pageCount`;
  * `book.isCompleted` = `pagesRead == pageCount`, so deleting a session reopens
    a completed book.
* **Time left** = pages left / speed. The speed is the pages per hour of this book's
  sessions, or of all sessions if the book has none yet. With no sessions at all,
  no estimate is shown.
* **Streak** = consecutive days with at least one session, ending today (or
  yesterday if nothing has been read yet today), in the device time zone.
* No Room schema change: the database is still version 1.

### Tests

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
```

The first command needs no device; the second needs a running emulator.

* `app/src/test/.../ReadingStatsTest.kt`: 25 JUnit tests on the pure functions. They
  cover 0 pages, exceeding the pages left, a book completed by its last session,
  going back to "in progress" after a delete, and a streak with a one-day gap.
* `app/src/androidTest/.../BookRepositorySessionTest.kt`: repository rules and
  transactions on an in-memory Room database.
* `app/src/androidTest/.../ReadingSessionUiTest.kt`: 8 Espresso tests:
  * a valid session appears in the list;
  * errors are shown for 0 pages, too many pages and an empty field;
  * delete with confirmation;
  * state survives `recreate()` on both screens.

See `HANDOFF.md` for the verification status and `docs/BUGS.md` for the bugs found.

## Team contributions

Use the shared repository with one branch per contributor and integrate changes
through pull requests. Add each member's name and contribution before submission.

* **Reading sessions & statistics** (branch `feature/reading-sessions-stats`):
  * session validation, plus edit and delete with transactional progress
    recalculation;
  * pure statistics functions (`domain/ReadingStats.kt`);
  * session list and form in the book detail screen;
  * `StatisticsViewModel` and the new statistics screen;
  * unit and Espresso tests, `docs/BUGS.md` and `HANDOFF.md`;
  * a fix for a startup crash in `BookshelfApplication`.
