package fr.estiam.traillog.bookshelf.data

/**
 * Persistent "books.json was already imported" marker, stored in Preferences DataStore
 * (see BookshelfApplication.importFlag). It makes the import run once per install,
 * even if the user later deletes every book.
 */
interface InitialImportFlag {
    suspend fun isDone(): Boolean
    suspend fun markDone()
}
