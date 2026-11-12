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
    val preferences = object : fr.estiam.traillog.bookshelf.ui.BookshelfPreferences {
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
    }
}
