package com.bdavidgm.notas.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

@Database(
    entities = [
        NoteEntity::class,
        TagEntity::class,
        NoteTagCrossRef::class,
        NoteImageEntity::class,
        NoteLinkCrossRef::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun notasDao(): NotasDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Añade [NoteEntity.uid] (estable para enlaces internos) y la tabla
         * [note_links]. Las notas existentes reciben un UUID en el backfill.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE notes ADD COLUMN uid TEXT NOT NULL DEFAULT ''",
                )
                db.query("SELECT id FROM notes WHERE uid = ''").use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        val uid = UUID.randomUUID().toString()
                        db.execSQL(
                            "UPDATE notes SET uid = ? WHERE id = ?",
                            arrayOf(uid, id),
                        )
                    }
                }
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_notes_uid ON notes(uid)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS note_links (
                        sourceNoteId INTEGER NOT NULL,
                        targetUid TEXT NOT NULL,
                        PRIMARY KEY(sourceNoteId, targetUid),
                        FOREIGN KEY(sourceNoteId) REFERENCES notes(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_note_links_sourceNoteId ON note_links(sourceNoteId)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_note_links_targetUid ON note_links(targetUid)",
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "notas.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
