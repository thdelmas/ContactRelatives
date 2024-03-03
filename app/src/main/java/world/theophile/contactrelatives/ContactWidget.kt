package world.theophile.contactrelatives

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.app.PendingIntent
import android.content.ComponentName
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.provider.ContactsContract
import android.util.Log
import android.widget.RemoteViews
import kotlin.math.log

class ContactWidget : AppWidgetProvider() {
    companion object {
        const val ACTION_DATA_UPDATED = "world.theophile.contactrelatives.ACTION_DATA_UPDATED"
    }
    private lateinit var dbHelper: ContactDatabaseHelper

    private lateinit var contactObserver: ContentObserver
    private var isUpdating = false


    private fun contactObserver(context: Context) = object : ContentObserver(Handler()) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, this@ContactWidget::class.java))
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }
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

        var contact: Triple<String, String, String>
        var ratio: Float
        var randomNumber: Int

        do {
            contact = contacts.random()
            val (proposedCounter, engagedCounter) = dbHelper.getCounters(contact.second)
            ratio = if (proposedCounter > 0) engagedCounter.toFloat() / proposedCounter else 0.0F
            randomNumber = (0..100).random()

            Log.d("ContactWidget", "Contact: ${contact.first}, proposedCounter: $proposedCounter, engagedCounter: $engagedCounter, Score: ${ratio * 100}%, randomNumber: >= $randomNumber%")
        } while (ratio * 100 < randomNumber)

        dbHelper.incrementProposedCounter(contact.second)
        return contact
    }


override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {

    synchronized(this) {
        if (isUpdating) {
            return
        }
        isUpdating = true
    }

    Log.d("ContactWidget", "onUpdate")
    dbHelper = ContactDatabaseHelper.getInstance(context)
    contactObserver = contactObserver(context)
    appWidgetIds.forEach { appWidgetId ->
        val (contactName, contactId, contactPhotoUri) = getRandomContact(context, "")
        RemoteViews(context.packageName, R.layout.simplified_widget_layout).apply {
            setTextViewText(R.id.contact_name, contactName)
            if (contactPhotoUri.isNotEmpty()) {
                setImageViewUri(R.id.contact_photo, Uri.parse(contactPhotoUri))
            } else {
                setImageViewResource(R.id.contact_photo, R.drawable.contact_photo_placeholder)
            }

            // Set up click listener for the refresh button
            val refreshIntent = Intent(context, ContactWidget::class.java).apply {
                action = "REFRESH_CONTACT"
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(context, appWidgetId, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            setOnClickPendingIntent(R.id.refresh_button, refreshPendingIntent)

            // Set up click listener for the contact picture/name
            val contactIntent = Intent(context, ContactWidget::class.java).apply {
                action = "CONTACT_ENGAGED"
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra("contactId", contactId)
            }
            val contactPendingIntent = PendingIntent.getBroadcast(context, appWidgetId + 1, contactIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            setOnClickPendingIntent(R.id.contact_name, contactPendingIntent)
            setOnClickPendingIntent(R.id.contact_photo, contactPendingIntent)
        }.also { appWidgetManager.updateAppWidget(appWidgetId, it) }
    }

        synchronized(this) {
            isUpdating = false
        }
    }

private fun startContactActivity(context: Context, contactId: String?) {
    val contactUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId)
    val contactIntent = Intent(Intent.ACTION_VIEW, contactUri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
    if (contactId != null) {
        dbHelper.incrementEngagedCounter(contactId)
    }
    context.startActivity(contactIntent)
}

private fun updateWidget(context: Context, appWidgetId: Int) {
    synchronized(this) {
        if (isUpdating) {
            return
        }
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            onUpdate(context, AppWidgetManager.getInstance(context), intArrayOf(appWidgetId))
        }
    }
}

override fun onReceive(context: Context, intent: Intent) {
    super.onReceive(context, intent)
    dbHelper = ContactDatabaseHelper.getInstance(context)
    val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
    val contactId = intent.getStringExtra("contactId")

    when (intent.action) {
        "CONTACT_ENGAGED" -> {
            startContactActivity(context, contactId)
            updateWidget(context, appWidgetId)
        }
        "REFRESH_CONTACT" -> {
            updateWidget(context, appWidgetId)
        }
        else -> {
            Log.d("ContactWidget", "onReceive: ${intent.action}")
        }
    }

    Log.d("ContactWidget", "onReceive: ${intent.action}")
}

    override fun onEnabled(context: Context) {
    super.onEnabled(context)
    Log.e("ContactWidget", "onEnabled")
    val filter = IntentFilter().apply { addAction(ACTION_DATA_UPDATED) }

    // Initialize the database helper
    dbHelper = ContactDatabaseHelper.getInstance(context)

    // Initialize the contactObserver
    contactObserver = contactObserver(context)

    // Get the AppWidgetManager instance
    val appWidgetManager = AppWidgetManager.getInstance(context)

    // Get the widget ids for this widget
    val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, ContactWidget::class.java))

        onUpdate(context, appWidgetManager, appWidgetIds)

    // Load a random contact for each widget
    appWidgetIds.forEach { appWidgetId ->
        val (contactName, contactId, contactPhotoUri) = getRandomContact(context, "")
        RemoteViews(context.packageName, R.layout.simplified_widget_layout).apply {
            setTextViewText(R.id.contact_name, contactName)
            if (contactPhotoUri.isNotEmpty()) {
                setImageViewUri(R.id.contact_photo, Uri.parse(contactPhotoUri))
            } else {
                setImageViewResource(R.id.contact_photo, R.drawable.contact_photo_placeholder)
            }

            // Set up click listener for the refresh button
            // Set up click listener for the refresh button
val refreshIntent = Intent(context, ContactWidget::class.java).apply {
    action = "REFRESH_CONTACT"
    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}
val refreshPendingIntent = PendingIntent.getBroadcast(context, appWidgetId * 10, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
setOnClickPendingIntent(R.id.refresh_button, refreshPendingIntent)

// Set up click listener for the contact picture/name
val contactIntent = Intent(context, ContactWidget::class.java).apply {
    action = "CONTACT_ENGAGED"
    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    putExtra("contactId", contactId)
}
val contactPendingIntent = PendingIntent.getBroadcast(context, appWidgetId * 10 + 1, contactIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
setOnClickPendingIntent(R.id.contact_name, contactPendingIntent)
setOnClickPendingIntent(R.id.contact_photo, contactPendingIntent)
        }.also { appWidgetManager.updateAppWidget(appWidgetId, it) }
    }
        onUpdate(context, appWidgetManager, appWidgetIds)

        filter.apply { addAction(Intent.ACTION_USER_PRESENT) }

    // Call contactObserver here
    context.contentResolver.registerContentObserver(ContactDatabaseHelper.CONTENT_URI, true, contactObserver)
}

}