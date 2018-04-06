package com.fsck.k9.activity

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.preference.CheckBoxPreference
import android.preference.Preference
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast

import com.fsck.k9.Preferences
import com.fsck.k9.R
import com.fsck.k9.helper.UnreadWidgetProperties
import com.fsck.k9.provider.UnreadWidgetProvider
import com.fsck.k9.search.SearchAccount

import timber.log.Timber


/**
 * Activity to select an account for the unread widget.
 */
class UnreadWidgetConfiguration : K9PreferenceActivity() {

    /**
     * The ID of the widget we are configuring.
     */
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private lateinit var unreadAccount: Preference
    private lateinit var unreadFolderEnabled: CheckBoxPreference
    private lateinit var unreadFolder: Preference

    private var selectedAccountUuid: String? = null
    private var selectedFolder: String? = null

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        // Find the widget ID from the intent.
        val extras = this.intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        }

        // If they gave us an intent without the widget ID, just bail.
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Timber.e("Received an invalid widget ID")
            finish()
            return
        }

        addPreferencesFromResource(R.xml.unread_widget_configuration)
        unreadAccount = findPreference(PREFERENCE_UNREAD_ACCOUNT)
        unreadAccount.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val intent = Intent(this@UnreadWidgetConfiguration, ChooseAccount::class.java)
            startActivityForResult(intent, REQUEST_CHOOSE_ACCOUNT)
            false
        }

        unreadFolderEnabled = findPreference(PREFERENCE_UNREAD_FOLDER_ENABLED) as CheckBoxPreference
        unreadFolderEnabled.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, _ ->
            unreadFolder.summary = getString(R.string.unread_widget_folder_summary)
            selectedFolder = null
            true
        }

        unreadFolder = findPreference(PREFERENCE_UNREAD_FOLDER)
        unreadFolder.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val intent = Intent(this@UnreadWidgetConfiguration, ChooseFolder::class.java)
            intent.putExtra(ChooseFolder.EXTRA_ACCOUNT, selectedAccountUuid)
            intent.putExtra(ChooseFolder.EXTRA_SHOW_DISPLAYABLE_ONLY, "yes")
            startActivityForResult(intent, REQUEST_CHOOSE_FOLDER)
            false
        }
        setTitle(R.string.unread_widget_select_account)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_CHOOSE_ACCOUNT -> handleChooseAccount(data.getStringExtra(ChooseAccount.EXTRA_ACCOUNT_UUID))
                REQUEST_CHOOSE_FOLDER -> {
                    val folderServerId = data.getStringExtra(ChooseFolder.EXTRA_NEW_FOLDER)
                    val folderDisplayName = data.getStringExtra(ChooseFolder.RESULT_FOLDER_DISPLAY_NAME)
                    handleChooseFolder(folderServerId, folderDisplayName)
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun handleChooseAccount(accountUuid: String) {
        val userSelectedSameAccount = accountUuid == selectedAccountUuid
        if (userSelectedSameAccount) {
            return
        }

        selectedAccountUuid = accountUuid
        selectedFolder = null
        unreadFolder.summary = getString(R.string.unread_widget_folder_summary)
        if (SearchAccount.UNIFIED_INBOX == selectedAccountUuid || SearchAccount.ALL_MESSAGES == selectedAccountUuid) {
            handleSearchAccount()
        } else {
            handleRegularAccount()
        }
    }

    private fun handleSearchAccount() {
        if (SearchAccount.UNIFIED_INBOX == selectedAccountUuid) {
            unreadAccount.setSummary(R.string.unread_widget_unified_inbox_account_summary)
        } else if (SearchAccount.ALL_MESSAGES == selectedAccountUuid) {
            unreadAccount.setSummary(R.string.unread_widget_all_messages_account_summary)
        }
        unreadFolderEnabled.isEnabled = false
        unreadFolderEnabled.isChecked = false
        unreadFolder.isEnabled = false
        selectedFolder = null
    }

    private fun handleRegularAccount() {
        val selectedAccount = Preferences.getPreferences(this).getAccount(selectedAccountUuid)
        val accountDescription: String? = selectedAccount.description
        val summary = if (accountDescription.isNullOrEmpty()) selectedAccount.email else accountDescription

        unreadAccount.summary = summary
        unreadFolderEnabled.isEnabled = true
        unreadFolder.isEnabled = true
    }

    private fun handleChooseFolder(folderServerId: String, folderDisplayName: String) {
        selectedFolder = folderServerId
        unreadFolder.summary = folderDisplayName
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.unread_widget_option, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.done -> {
                if (validateWidget()) {
                    updateWidgetAndExit()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun validateWidget(): Boolean {
        if (selectedAccountUuid == null) {
            Toast.makeText(this, R.string.unread_widget_account_not_selected, Toast.LENGTH_LONG).show()
            return false
        } else if (unreadFolderEnabled.isChecked && selectedFolder == null) {
            Toast.makeText(this, R.string.unread_widget_folder_not_selected, Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    private fun updateWidgetAndExit() {
        val properties = UnreadWidgetProperties(appWidgetId, selectedAccountUuid!!, selectedFolder)
        saveWidgetProperties(this, properties)

        // Update widget
        val context = applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(context)
        UnreadWidgetProvider.updateWidget(context, appWidgetManager, properties)

        // Let the caller know that the configuration was successful
        val resultValue = Intent()
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(Activity.RESULT_OK, resultValue)
        finish()
    }

    companion object {
        /**
         * Name of the preference file to store the widget configuration.
         */
        private const val PREFS_NAME = "unread_widget_configuration.xml"

        /**
         * Prefixes for the preference keys
         */
        private const val PREF_PREFIX_KEY = "unread_widget."
        private const val PREF_FOLDER_NAME_SUFFIX_KEY = ".folder_name"

        private const val PREFERENCE_UNREAD_ACCOUNT = "unread_account"
        private const val PREFERENCE_UNREAD_FOLDER_ENABLED = "unread_folder_enabled"
        private const val PREFERENCE_UNREAD_FOLDER = "unread_folder"

        private const val REQUEST_CHOOSE_ACCOUNT = 1
        private const val REQUEST_CHOOSE_FOLDER = 2

        private fun saveWidgetProperties(context: Context, properties: UnreadWidgetProperties) {
            val appWidgetId = properties.appWidgetId
            val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            editor.putString(PREF_PREFIX_KEY + appWidgetId, properties.accountUuid)
            editor.putString(PREF_PREFIX_KEY + appWidgetId + PREF_FOLDER_NAME_SUFFIX_KEY, properties.folderServerId)
            editor.apply()
        }

        fun getWidgetProperties(context: Context, appWidgetId: Int): UnreadWidgetProperties {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val accountUuid = prefs.getString(PREF_PREFIX_KEY + appWidgetId, null)
            val folderServerId = prefs.getString(PREF_PREFIX_KEY + appWidgetId + PREF_FOLDER_NAME_SUFFIX_KEY, null)
            return UnreadWidgetProperties(appWidgetId, accountUuid!!, folderServerId)
        }

        fun deleteWidgetConfiguration(context: Context, appWidgetId: Int) {
            val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            editor.remove(PREF_PREFIX_KEY + appWidgetId)
            editor.remove(PREF_PREFIX_KEY + appWidgetId + PREF_FOLDER_NAME_SUFFIX_KEY)
            editor.apply()
        }
    }
}
