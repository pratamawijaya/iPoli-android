package io.ipoli.android.common.di

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.view.LayoutInflater
import com.bluelinelabs.conductor.Router
import com.couchbase.lite.Database
import com.couchbase.lite.DatabaseConfiguration
import io.ipoli.android.common.navigation.Navigator
import io.ipoli.android.common.text.CalendarFormatter
import io.ipoli.android.player.persistence.CouchbasePlayerRepository
import io.ipoli.android.player.persistence.PlayerRepository
import io.ipoli.android.quest.calendar.CalendarPresenter
import io.ipoli.android.quest.calendar.addquest.AddQuestPresenter
import io.ipoli.android.quest.calendar.dayview.DayViewPresenter
import io.ipoli.android.quest.data.persistence.CouchbaseQuestRepository
import io.ipoli.android.quest.data.persistence.QuestRepository
import io.ipoli.android.quest.usecase.*
import io.ipoli.android.reminder.ReminderScheduler
import io.ipoli.android.reminder.view.formatter.ReminderTimeFormatter
import io.ipoli.android.reminder.view.formatter.TimeUnitFormatter
import io.ipoli.android.reminder.view.picker.ReminderPickerDialogPresenter
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.Job
import space.traversal.kapsule.HasModules
import space.traversal.kapsule.Injects
import space.traversal.kapsule.required

/**
 * Created by Venelin Valkov <venelin@ipoli.io>
 * on 9/10/17.
 */
interface RepositoryModule {
    val questRepository: QuestRepository
    val playerRepository: PlayerRepository
}

class CouchbaseRepositoryModule : RepositoryModule, Injects<ControllerModule> {
    private val database by required { database }
    private val job by required { job }
    override val questRepository get() = CouchbaseQuestRepository(database, job + CommonPool)
    override val playerRepository get() = CouchbasePlayerRepository(database, job + CommonPool)
}

class CouchbaseJobRepositoryModule : RepositoryModule, Injects<JobModule> {
    private val database by required { database }
    private val job by required { job }
    override val questRepository get() = CouchbaseQuestRepository(database, job + CommonPool)
    override val playerRepository get() = CouchbasePlayerRepository(database, job + CommonPool)
}

interface AndroidModule {
    val layoutInflater: LayoutInflater

    val sharedPreferences: SharedPreferences

    val reminderTimeFormatter: ReminderTimeFormatter

    val timeUnitFormatter: TimeUnitFormatter

    val calendarFormatter: CalendarFormatter

    val database: Database

    val reminderScheduler: ReminderScheduler

    val job: Job
}

interface NavigationModule {
    val navigator: Navigator
}

class AndroidNavigationModule(private val router: Router?) : NavigationModule {
    override val navigator get() = Navigator(router)
}

class MainAndroidModule(private val context: Context) : AndroidModule {
    override val layoutInflater: LayoutInflater get() = LayoutInflater.from(context)

    override val sharedPreferences: SharedPreferences
        get() = PreferenceManager.getDefaultSharedPreferences(context)

    override val reminderTimeFormatter get() = ReminderTimeFormatter(context)

    override val timeUnitFormatter get() = TimeUnitFormatter(context)

    override val calendarFormatter get() = CalendarFormatter(context)

    override val reminderScheduler get() = ReminderScheduler()

    override val database: Database
        get() = Database("iPoli", DatabaseConfiguration(context.applicationContext))

    override val job get() = Job()
}

class MainUseCaseModule : UseCaseModule, Injects<ControllerModule> {
    private val questRepository by required { questRepository }
    private val playerRepository by required { playerRepository }
    private val reminderScheduler by required { reminderScheduler }
    private val job by required { job }
    override val loadScheduleForDateUseCase
        get() = LoadScheduleForDateUseCase(questRepository, job + CommonPool)
    override val saveQuestUseCase get() = SaveQuestUseCase(questRepository, reminderScheduler)
    override val removeQuestUseCase get() = RemoveQuestUseCase(questRepository)
    override val undoRemoveQuestUseCase get() = UndoRemovedQuestUseCase(questRepository)
    override val findQuestToRemindUseCase get() = FindQuestsToRemindUseCase(questRepository)
    override val snoozeQuestUseCase get() = SnoozeQuestUseCase(questRepository, reminderScheduler)
    override val completeQuestUseCase get() = CompleteQuestUseCase(questRepository, reminderScheduler)
    override val undoCompleteQuestUseCase get() = UndoCompleteQuestUseCase(questRepository, reminderScheduler)
}

interface JobUseCaseModule {
    val findQuestToRemindUseCase: FindQuestsToRemindUseCase
    val snoozeQuestUseCase: SnoozeQuestUseCase
    val completeQuestUseCase: CompleteQuestUseCase
}

class AndroidJobUseCaseModule : JobUseCaseModule, Injects<JobModule> {
    private val questRepository by required { questRepository }
    private val reminderScheduler by required { reminderScheduler }
    override val findQuestToRemindUseCase get() = FindQuestsToRemindUseCase(questRepository)
    override val snoozeQuestUseCase get() = SnoozeQuestUseCase(questRepository, reminderScheduler)
    override val completeQuestUseCase get() = CompleteQuestUseCase(questRepository, reminderScheduler)
}

interface UseCaseModule {
    val loadScheduleForDateUseCase: LoadScheduleForDateUseCase
    val saveQuestUseCase: SaveQuestUseCase
    val removeQuestUseCase: RemoveQuestUseCase
    val undoRemoveQuestUseCase: UndoRemovedQuestUseCase
    val findQuestToRemindUseCase: FindQuestsToRemindUseCase
    val snoozeQuestUseCase: SnoozeQuestUseCase
    val completeQuestUseCase: CompleteQuestUseCase
    val undoCompleteQuestUseCase: UndoCompleteQuestUseCase
}

interface PresenterModule {
    val dayViewPresenter: DayViewPresenter
    val reminderPickerPresenter: ReminderPickerDialogPresenter
    val calendarPresenter: CalendarPresenter
    val addQuestPresenter: AddQuestPresenter
}

class AndroidPresenterModule : PresenterModule, Injects<ControllerModule> {
    private val loadScheduleForDateUseCase by required { loadScheduleForDateUseCase }
    private val saveQuestUseCase by required { saveQuestUseCase }
    private val removeQuestUseCase by required { removeQuestUseCase }
    private val undoRemoveQuestUseCase by required { undoRemoveQuestUseCase }
    private val completeQuestUseCase by required { completeQuestUseCase }
    private val undoCompleteQuestUseCase by required { undoCompleteQuestUseCase }
    private val navigator by required { navigator }
    private val reminderTimeFormatter by required { reminderTimeFormatter }
    private val timeUnitFormatter by required { timeUnitFormatter }
    private val calendarFormatter by required { calendarFormatter }
    private val job by required { job }
    override val dayViewPresenter get() = DayViewPresenter(loadScheduleForDateUseCase, saveQuestUseCase, removeQuestUseCase, undoRemoveQuestUseCase, completeQuestUseCase, undoCompleteQuestUseCase, job)
    override val reminderPickerPresenter get() = ReminderPickerDialogPresenter(reminderTimeFormatter, timeUnitFormatter, job)
    override val calendarPresenter get() = CalendarPresenter(calendarFormatter, job)
    override val addQuestPresenter get() = AddQuestPresenter(saveQuestUseCase, job)
}

class ControllerModule(androidModule: AndroidModule,
                       navigationModule: NavigationModule,
                       repositoryModule: RepositoryModule,
                       useCaseModule: UseCaseModule,
                       presenterModule: PresenterModule) :
    AndroidModule by androidModule,
    NavigationModule by navigationModule,
    RepositoryModule by repositoryModule,
    UseCaseModule by useCaseModule,
    PresenterModule by presenterModule,
    HasModules {
    override val modules = setOf(androidModule, navigationModule, repositoryModule, useCaseModule, presenterModule)
}

class JobModule(androidModule: AndroidModule,
                repositoryModule: RepositoryModule,
                useCaseModule: JobUseCaseModule) :
    AndroidModule by androidModule,
    RepositoryModule by repositoryModule,
    JobUseCaseModule by useCaseModule,
    HasModules {
    override val modules = setOf(androidModule, repositoryModule, useCaseModule)
}