package fr.luteal.app

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import fr.luteal.app.widget.WidgetDataObserver
import fr.luteal.app.widget.WidgetWorkScheduler
import dagger.hilt.android.HiltAndroidApp
import org.acra.config.dialog
import org.acra.config.mailSender
import org.acra.data.StringFormat
import org.acra.ktx.initAcra
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@HiltAndroidApp
class LutealApp : Application(), Configuration.Provider {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Inject
    lateinit var widgetDataObserver: WidgetDataObserver

    @Inject
    lateinit var widgetWorkScheduler: WidgetWorkScheduler

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        initAcra {
            buildConfigClass = BuildConfig::class.java
            reportFormat = StringFormat.JSON
            dialog {
                text = getString(R.string.crash_dialog_text)
                title = getString(R.string.crash_dialog_title)
                commentPrompt = getString(R.string.crash_dialog_comment)
                resTheme = R.style.Theme_Luteal
            }
            mailSender {
                mailTo = "joeri.waldemar@gmail.com"
                subject = "Rapport de plantage Luteal"
                body = getString(R.string.crash_mail_body)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        widgetDataObserver.start(applicationScope)
        widgetWorkScheduler.reconcileSchedules()
    }

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    // On-demand WorkManager initialization so @HiltWorker workers are created
    // by Hilt. The default androidx.startup initializer is removed in the
    // manifest (see AndroidManifest.xml).
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
