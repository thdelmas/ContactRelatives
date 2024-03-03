package world.theophile.contactrelatives


import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

class ContactProvider : ContentProvider() {

    private lateinit var dbHelper: ContactDatabaseHelper

    override fun onCreate(): Boolean {
        dbHelper = ContactDatabaseHelper.getInstance(context!!)
        return true
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        return dbHelper.readableDatabase.query(ContactDatabaseHelper.TABLE_NAME, projection, selection, selectionArgs, null, null, sortOrder)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val id = dbHelper.writableDatabase.insert(ContactDatabaseHelper.TABLE_NAME, null, values)
        context?.contentResolver?.notifyChange(uri, null)
        return Uri.withAppendedPath(uri, id.toString())
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int {
        val count = dbHelper.writableDatabase.update(ContactDatabaseHelper.TABLE_NAME, values, selection, selectionArgs)
        if (count > 0) context?.contentResolver?.notifyChange(uri, null)
        return count
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        val count = dbHelper.writableDatabase.delete(ContactDatabaseHelper.TABLE_NAME, selection, selectionArgs)
        if (count > 0) context?.contentResolver?.notifyChange(uri, null)
        return count
    }

    override fun getType(uri: Uri): String? {
        return null
    }
}