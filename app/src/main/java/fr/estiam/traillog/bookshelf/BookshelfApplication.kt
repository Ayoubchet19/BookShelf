package fr.estiam.traillog.bookshelf

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import fr.estiam.traillog.bookshelf.data.BookDatabase
import fr.estiam.traillog.bookshelf.data.BookRepository
import fr.estiam.traillog.bookshelf.data.InitialImportFlag
import fr.estiam.traillog.bookshelf.model.SortOrder
import fr.estiam.traillog.bookshelf.model.UserPreferences
import fr.estiam.traillog.bookshelf.ui.BookshelfPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException

private val Application.dataStore by preferencesDataStore("bookshelf_preferences")

private val SORT = stringPreferencesKey("sort")
private val DARK_MODE = booleanPreferencesKey("dark_mode")
private val SHOW_COMPLETED = booleanPreferencesKey("show_completed")
private val INITIAL_IMPORT_DONE = booleanPreferencesKey("initial_import_done")

class BookshelfApplication : Application() {
    private val database by lazy { Room.databaseBuilder(this, BookDatabase::class.java, "bookshelf.db").build() }
    val repository by lazy { BookRepository(database, this) }

    /** Lives as long as the process; used to observe the dark-mode preference. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** DataStore content; a read error (corrupted file) gives the default values instead of a crash. */
    private val storedValues by lazy {
        dataStore.data.catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
    }

    // Must be lazy: the DataStore delegate needs applicationContext, which is still null
    // while the Application constructor runs (eager init crashed the app on launch, see docs/BUGS.md).
    val preferences: BookshelfPreferences by lazy { object : BookshelfPreferences {
        override val flow = storedValues.map { values ->
            UserPreferences(
                sort = SortOrder.normalize(values[SORT]),
                darkMode = values[DARK_MODE] ?: false,
                showCompleted = values[SHOW_COMPLETED] ?: true
            )
        }
        override suspend fun setSort(value: String) { dataStore.edit { it[SORT] = SortOrder.normalize(value) } }
        override suspend fun setDarkMode(value: Boolean) { dataStore.edit { it[DARK_MODE] = value } }
        override suspend fun setShowCompleted(value: Boolean) { dataStore.edit { it[SHOW_COMPLETED] = value } }
    } }

    /** "books.json already imported" marker, read by BookRepository.seedIfNeeded. */
    val importFlag: InitialImportFlag by lazy { object : InitialImportFlag {
        override suspend fun isDone() = storedValues.first()[INITIAL_IMPORT_DONE] == true
        override suspend fun markDone() { dataStore.edit { it[INITIAL_IMPORT_DONE] = true } }
    } }

    override fun onCreate() {
        super.onCreate()
        // Dark mode preference: applied to every open activity as soon as it changes (no restart).
        appScope.launch {
            preferences.flow.map { it.darkMode }.distinctUntilChanged().collect { dark ->
                AppCompatDelegate.setDefaultNightMode(
                    if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                )
            }
        }
    }
}
