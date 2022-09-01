package de.lmu.arcasegrammar.model

import android.content.Context
import androidx.room.*
import de.lmu.arcasegrammar.sentencebuilder.Sentence
import de.lmu.arcasegrammar.sentencebuilder.SentenceDao

@Database(entities = [Sentence::class], version = 2, exportSchema = true, autoMigrations = [ AutoMigration (from = 1, to = 2) ])
@TypeConverters(MyTypeConverters::class)
abstract class HistoryDatabase : RoomDatabase() {

    abstract fun sentenceDao(): SentenceDao
    // TODO: add additional QuizWrapperDaos here

    companion object {
        @Volatile
        private var INSTANCE: HistoryDatabase? = null

        fun getDatabase(context: Context): HistoryDatabase {
            val tempInstance =
                INSTANCE
            if (tempInstance != null) {
                return tempInstance
            }
            synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HistoryDatabase::class.java,
                    "history_database"
                )
                    .build()
                INSTANCE = instance
                return instance
            }
        }
    }
}