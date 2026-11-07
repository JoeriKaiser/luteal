package fr.luteal.app.notification

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fr.luteal.app.R
import fr.luteal.core.model.NotificationType
import fr.luteal.core.model.NotificationVisibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotificationContentResolverTest {

    private lateinit var context: Context
    private lateinit var resolver: NotificationContentResolver

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        resolver = NotificationContentResolver(context)
    }

    @Test
    fun concealedModeProducesNeutralCopyWithoutMedicalDetails() {
        val content = resolver.resolve(
            type = NotificationType.DAILY_CHECK_IN,
            visibility = NotificationVisibility.CONCEALED
        )

        assertEquals(context.getString(R.string.notif_default_concealed_title), content.title)
        assertEquals(context.getString(R.string.notif_default_concealed_body), content.body)
        assertEquals(context.getString(R.string.notif_default_public_title), content.publicTitle)
        assertEquals(NotificationChannelManager.CHANNEL_DAILY_CHECKIN, content.channelId)

        // Ensure no explicit clinical keywords leak into concealed copy
        assertFalse(content.body.contains("règles", ignoreCase = true))
        assertFalse(content.body.contains("ovulation", ignoreCase = true))
        assertFalse(content.body.contains("symptôme", ignoreCase = true))
    }

    @Test
    fun descriptiveModeProvidesDetailedContext() {
        val dailyContent = resolver.resolve(
            type = NotificationType.DAILY_CHECK_IN,
            visibility = NotificationVisibility.DESCRIPTIVE
        )
        assertEquals(context.getString(R.string.notif_daily_descriptive_title), dailyContent.title)
        assertTrue(dailyContent.body.contains("observations"))

        val windowContent = resolver.resolve(
            type = NotificationType.PERIOD_WINDOW,
            visibility = NotificationVisibility.DESCRIPTIVE
        )
        assertEquals(context.getString(R.string.notif_window_descriptive_title), windowContent.title)
        assertEquals(NotificationChannelManager.CHANNEL_PERIOD_WINDOW, windowContent.channelId)
    }

    @Test
    fun customModeUsesUserDefinedStrings() {
        val content = resolver.resolve(
            type = NotificationType.DAILY_CHECK_IN,
            visibility = NotificationVisibility.CUSTOM,
            customTitle = "Mon moment calme",
            customBody = "Prendre soin de moi ce soir."
        )

        assertEquals("Mon moment calme", content.title)
        assertEquals("Prendre soin de moi ce soir.", content.body)
        assertEquals(context.getString(R.string.notif_default_public_title), content.publicTitle)
    }

    @Test
    fun pillReminderConcealedModeProvidesConcealedActionsAndChannel() {
        val content = resolver.resolve(
            type = NotificationType.PILL_REMINDER,
            visibility = NotificationVisibility.CONCEALED
        )

        assertEquals(NotificationChannelManager.CHANNEL_PILL_REMINDER, content.channelId)
        assertEquals(context.getString(R.string.notif_default_concealed_title), content.title)
        assertEquals(context.getString(R.string.notif_default_concealed_body), content.body)
        assertEquals(context.getString(R.string.notif_pill_action_taken_concealed), content.actionTakenLabel)
        assertEquals(context.getString(R.string.notif_pill_action_snooze), content.actionSnoozeLabel)
        assertEquals(context.getString(R.string.notif_default_public_title), content.publicTitle)
    }

    @Test
    fun pillReminderDescriptiveModeProvidesDescriptiveActionsAndCopy() {
        val content = resolver.resolve(
            type = NotificationType.PILL_REMINDER,
            visibility = NotificationVisibility.DESCRIPTIVE
        )

        assertEquals(NotificationChannelManager.CHANNEL_PILL_REMINDER, content.channelId)
        assertEquals(context.getString(R.string.notif_pill_descriptive_title), content.title)
        assertEquals(context.getString(R.string.notif_pill_descriptive_body), content.body)
        assertEquals(context.getString(R.string.notif_pill_action_taken_descriptive), content.actionTakenLabel)
        assertEquals(context.getString(R.string.notif_pill_action_snooze), content.actionSnoozeLabel)
    }

    @Test
    fun nonPillNotificationsDoNotExposePillActions() {
        val content = resolver.resolve(
            type = NotificationType.DAILY_CHECK_IN,
            visibility = NotificationVisibility.DESCRIPTIVE
        )
        assertNull(content.actionTakenLabel)
        assertNull(content.actionSnoozeLabel)
    }
}
