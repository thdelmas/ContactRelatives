package world.theophile.contactrelatives

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.app.PendingIntent
import android.provider.ContactsContract
import android.widget.RemoteViews

class ContactWidget : AppWidgetProvider() {

    private fun getRandomContact(context: Context, lastContactId: String): Triple<String, String, String> {
    val cursor = context.contentResolver.query(
        ContactsContract.Contacts.CONTENT_URI,
        null,
        null,
        null,
        null
    )

    val contacts = mutableListOf<Triple<String, String, String>>()
    cursor?.use {
        val idColumnIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
        val nameColumnIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
        val photoUriColumnIndex = it.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
        if (idColumnIndex != -1 && nameColumnIndex != -1 && photoUriColumnIndex != -1) {
            while (it.moveToNext()) {
                val id = it.getString(idColumnIndex)
                val name = it.getString(nameColumnIndex)
                val photoUri = it.getString(photoUriColumnIndex)
                if (id != lastContactId) {
                    contacts.add(Triple(name, id, photoUri ?: ""))
                }
            }
        }
    }

    return if (contacts.isNotEmpty()) {
        contacts.random()
    } else {
        Triple("No contacts found", "", "")
    }
}

override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
    val sharedPreferences = context.getSharedPreferences("ContactWidget", Context.MODE_PRIVATE)
    val lastContactId = sharedPreferences.getString("lastContactId", "")

    appWidgetIds.forEach { appWidgetId ->
        val (contactName, contactId, contactPhotoUri) = getRandomContact(context, lastContactId ?: "")
        RemoteViews(context.packageName, R.layout.simplified_widget_layout).apply {
            setTextViewText(R.id.contact_name, contactName)
            if (contactPhotoUri.isNotEmpty()) {
                setImageViewUri(R.id.contact_photo, Uri.parse(contactPhotoUri))
            } else {
                setImageViewResource(R.id.contact_photo, R.drawable.contact_photo_placeholder)
            }
            setOnClickPendingIntent(R.id.refresh_button, getRefreshPendingIntent(context, appWidgetId))
            setOnClickPendingIntent(R.id.contact_name, getPendingIntent(context, contactId))
        }.also { views ->
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        with(sharedPreferences.edit()) {
            putString("lastContactId", contactId)
            apply()
        }
    }
}
private fun getRefreshPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
    val intent = Intent(context, ContactWidget::class.java).apply {
        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
    }
    return PendingIntent.getBroadcast(context, appWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
    private fun getPendingIntent(context: Context, contactId: String): PendingIntent {
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId)
        val intent = Intent(Intent.ACTION_VIEW, uri)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getActivity(context, 0, intent, flags)
    }
}