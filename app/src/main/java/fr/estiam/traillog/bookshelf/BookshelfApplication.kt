package fr.estiam.traillog.bookshelf

import android.app.Application
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import fr.estiam.traillog.bookshelf.data.BookDatabase
import fr.estiam.traillog.bookshelf.data.BookRepository
import fr.estiam.traillog.bookshelf.model.UserPreferences
import kotlinx.coroutines.flow.map

private val Application.dataStore by preferencesDataStore("bookshelf_preferences")

class BookshelfApplication : Application() {
    private val database by lazy { Room.databaseBuilder(this, BookDatabase::class.java, "bookshelf.db").build() }
    val repository by lazy { BookRepository(database, this) }
    // Must be lazy: the DataStore delegate needs applicationContext, which is still null
    // while the Application constructor runs (eager init crashed the app on launch, see docs/BUGS.md).
    val preferences: fr.estiam.traillog.bookshelf.ui.BookshelfPreferences by lazy { object : fr.estiam.traillog.bookshelf.ui.BookshelfPreferences {
        override val flow = dataStore.data.map { values ->
            UserPreferences(
                sort = values[stringPreferencesKey("sort")] ?: "TITLE",
                darkMode = values[booleanPreferencesKey("dark_mode")] ?: false,
                showCompleted = values[booleanPreferencesKey("show_completed")] ?: true
            )
        }
        override suspend fun setSort(value: String) { dataStore.edit { it[stringPreferencesKey("sort")] = value } }
        override suspend fun setDarkMode(value: Boolean) { dataStore.edit { it[booleanPreferencesKey("dark_mode")] = value } }
        override suspend fun setShowCompleted(value: Boolean) { dataStore.edit { it[booleanPreferencesKey("show_completed")] = value } }
    } }
}
