package world.theophile.contactrelatives


import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.util.Log

class ContactDatabaseHelper(private val context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private var instance: ContactDatabaseHelper? = null

        const val DATABASE_VERSION = 1
        const val DATABASE_NAME = "ContactDatabase.db"
        const val TABLE_NAME = "contact"
        const val COLUMN_NAME_CONTACT_ID = "contact_id"
        const val COLUMN_NAME_PROPOSED_COUNTER = "proposed_counter"
        const val COLUMN_NAME_ENGAGED_COUNTER = "engaged_counter"
        val CONTENT_URI = Uri.parse("content://world.theophile.contactrelatives.provider/contact")

        @Synchronized
        fun getInstance(context: Context): ContactDatabaseHelper {
            if (instance == null) {
                instance = ContactDatabaseHelper(context.applicationContext)
            }
            return instance!!
        }
    }

@Synchronized
fun getCounters(contactId: String): Pair<Int, Int> {
    val proposedCounter = getColumnValue(COLUMN_NAME_PROPOSED_COUNTER, contactId)
    val engagedCounter = getColumnValue(COLUMN_NAME_ENGAGED_COUNTER, contactId)

    Log.d("ContactDatabaseHelper", "getCounters: contactId = $contactId, proposedCounter = $proposedCounter, engagedCounter = $engagedCounter")

    return Pair(proposedCounter, engagedCounter)
}

@Synchronized
fun getColumnValue(columnName: String, contactId: String): Int {
    val db = readableDatabase
    val cursor = db.rawQuery(
        "SELECT $columnName FROM $TABLE_NAME WHERE $COLUMN_NAME_CONTACT_ID = ?",
        arrayOf(contactId)
    )
    var value = 0
    if (cursor.moveToFirst()) {
        value = cursor.getInt(0)
    }
    cursor.close()
    return value
}
    fun contactExists(contactId: String): Boolean {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT 1 FROM $TABLE_NAME WHERE $COLUMN_NAME_CONTACT_ID = ?",
            arrayOf(contactId)
        )
        val exists = cursor.moveToFirst()
        cursor.close()
        return exists
    }

    @Synchronized
fun createContactIfNotExists(contactId: String) {
    if (!contactExists(contactId)) {
        val values = ContentValues().apply {
            put(COLUMN_NAME_CONTACT_ID, contactId)
            put(COLUMN_NAME_PROPOSED_COUNTER, 0)
            put(COLUMN_NAME_ENGAGED_COUNTER, 0)
        }
        val contactUri = Uri.withAppendedPath(CONTENT_URI, contactId)
        context.contentResolver.insert(contactUri, values)
    }
}

@Synchronized
fun incrementProposedCounter(contactId: String) {
    val db = writableDatabase
    try {
        db.beginTransaction()
        createContactIfNotExists(contactId)
        val values = ContentValues().apply {
            put(
                COLUMN_NAME_PROPOSED_COUNTER,
                getColumnValue(COLUMN_NAME_PROPOSED_COUNTER, contactId) + 1
            )
        }
        db.update(
            TABLE_NAME,
            values,
            "$COLUMN_NAME_CONTACT_ID = ?",
            arrayOf(contactId)
        )
        db.setTransactionSuccessful()
    } catch (e: Exception) {
        // Handle exception
    } finally {
        db.endTransaction()
    }
}

@Synchronized
fun incrementEngagedCounter(contactId: String) {
    val db = writableDatabase
    try {
        db.beginTransaction()
        createContactIfNotExists(contactId)
        val values = ContentValues().apply {
            put(
                COLUMN_NAME_ENGAGED_COUNTER,
                getColumnValue(COLUMN_NAME_ENGAGED_COUNTER, contactId) + 1
            )
        }
        db.update(
            TABLE_NAME,
            values,
            "$COLUMN_NAME_CONTACT_ID = ?",
            arrayOf(contactId)
        )
        db.setTransactionSuccessful()
    } catch (e: Exception) {
        // Handle exception
    } finally {
        db.endTransaction()
    }
}

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE $TABLE_NAME (" +
                    "$COLUMN_NAME_CONTACT_ID TEXT PRIMARY KEY," +
                    "$COLUMN_NAME_PROPOSED_COUNTER INTEGER DEFAULT 0," +
                    "$COLUMN_NAME_ENGAGED_COUNTER INTEGER DEFAULT 0)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }
}