package io.ipoli.android.quest.calendar

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.support.annotation.ColorRes
import android.support.v4.content.ContextCompat
import android.support.v4.widget.TextViewCompat
import android.support.v4.widget.TintableCompoundButton
import android.text.Editable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.StrikethroughSpan
import android.util.TypedValue
import android.view.*
import android.widget.LinearLayout
import io.ipoli.android.R
import io.ipoli.android.common.ViewUtils
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.common.di.Module
import io.ipoli.android.common.mvi.MviViewController
import io.ipoli.android.common.mvi.ViewStateRenderer
import io.ipoli.android.common.ui.Color
import io.ipoli.android.common.ui.ColorPickerDialogController
import io.ipoli.android.iPoliApp
import io.ipoli.android.quest.calendar.ui.dayview.*
import io.ipoli.android.quest.data.Category
import io.ipoli.android.quest.persistence.RealmQuestRepository
import io.ipoli.android.quest.usecase.LoadScheduleForDateUseCase
import io.reactivex.Observable
import kotlinx.android.synthetic.main.calendar_hour_cell.view.*
import kotlinx.android.synthetic.main.controller_day_view.view.*
import kotlinx.android.synthetic.main.item_calendar_drag.view.*
import kotlinx.android.synthetic.main.item_calendar_quest.view.*
import kotlinx.android.synthetic.main.unscheduled_quest_item.view.*
import org.threeten.bp.LocalDate
import space.traversal.kapsule.Injects
import space.traversal.kapsule.inject
import space.traversal.kapsule.required
import timber.log.Timber

interface DayView : ViewStateRenderer<DayViewState> {
    fun loadScheduleIntent(): Observable<LocalDate>
}

sealed class DayViewState {
    object Loading : DayViewState()
    data class ScheduleLoaded(val scheduledQuests: List<DayViewController.QuestViewModel>,
                              val unscheduledQuests: List<DayViewController.UnscheduledQuestViewModel>) : DayViewState()
}

class DayViewController :
    MviViewController<DayViewState, DayView, DayViewPresenter>(R.layout.controller_day_view),
    Injects<Module>,
    CalendarChangeListener,
    DayView {

    private val presenter by required { dayViewPresenter }

    private lateinit var calendarDayView: CalendarDayView

    override fun bindView(view: View) {
        calendarDayView = view.calendar
        calendarDayView.setCalendarChangeListener(this)
        calendarDayView.setHourAdapter(object : HourCellAdapter {
            override fun bind(view: View, hour: Int) {
                if (hour > 0) {
                    view.timeLabel.text = hour.toString() + ":00"
                }
            }
        })


        calendarDayView.scrollToNow()
    }

    override fun loadScheduleIntent(): Observable<LocalDate> {
        return Observable.just(LocalDate.now())
            .filter { !isRestoring }
    }

    override fun createPresenter(): DayViewPresenter {
        // @TODO fix me
        return DayViewPresenter(LoadScheduleForDateUseCase(RealmQuestRepository()))
//        return presenter
    }

    override fun render(state: DayViewState, view: View) {
        if (state is DayViewState.ScheduleLoaded) {
            eventsAdapter = QuestScheduledEventsAdapter(activity!!, state.scheduledQuests, calendarDayView)
            calendarDayView.setScheduledEventsAdapter(eventsAdapter)
            unscheduledEventsAdapter = UnscheduledQuestsAdapter(state.unscheduledQuests, calendarDayView)
            calendarDayView.setUnscheduledQuestsAdapter(unscheduledEventsAdapter)
        }

    }

    override fun onStartEditScheduledEvent(dragView: View, startTime: Time, endTime: Time, name: String, color: Color) {
        startActionMode()
        dragView.dragStartTime.text = startTime.toString()
        dragView.dragEndTime.text = endTime.toString()
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(dragView.dragStartTime, 8, 14, 1, TypedValue.COMPLEX_UNIT_SP)
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(dragView.dragEndTime, 8, 14, 1, TypedValue.COMPLEX_UNIT_SP)
        setupDragViewNameAndColor(dragView, name, color)
    }

    override fun onStartEditUnscheduledEvent(dragView: View, name: String, color: Color) {
        startActionMode()
        dragView.dragStartTime.visibility = View.GONE
        dragView.dragEndTime.visibility = View.GONE
        setupDragViewNameAndColor(dragView, name, color)
    }

    private fun setupDragViewNameAndColor(dragView: View, name: String, color: Color) {
        dragView.dragName.setText(name)
        dragView.setBackgroundColor(ContextCompat.getColor(dragView.context, color.color500))

        dragView.dragName.setOnFocusChangeListener { _, isFocused ->
            if (isFocused) {
                calendarDayView.startEditDragEventName()
            }
        }

        dragView.dragName.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {

            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                calendarDayView.updateDragEventName(text.toString())
            }

        })
    }

    override fun onDragViewClick(dragView: View) {
        ViewUtils.hideKeyboard(calendarDayView)
        dragView.requestFocus()
    }

    override fun onDragViewColorChange(dragView: View, color: Color) {
        ObjectAnimator.ofArgb(
            dragView,
            "backgroundColor",
            (dragView.background as ColorDrawable).color,
            ContextCompat.getColor(dragView.context, color.color500)
        )
            .setDuration(dragView.context.resources.getInteger(android.R.integer.config_longAnimTime).toLong())
            .start()
    }

    override fun onRescheduleScheduledEvent(position: Int, startTime: Time, duration: Int) {
        stopActionMode()
        ViewUtils.hideKeyboard(calendarDayView)
        eventsAdapter.rescheduleEvent(position, startTime, duration)
    }

    override fun onScheduleUnscheduledEvent(position: Int, startTime: Time) {
        stopActionMode()
        ViewUtils.hideKeyboard(calendarDayView)
        val ue = unscheduledEventsAdapter.removeEvent(position)
        val vm = QuestViewModel(ue.name, ue.duration, startTime.toMinuteOfDay(), startTime.toString(), "12:00", ue.backgroundColor, Category.FUN.color800, false)
        eventsAdapter.addEvent(vm)
    }

    override fun onUnscheduleScheduledEvent(position: Int) {
        stopActionMode()
        ViewUtils.hideKeyboard(calendarDayView)
        val e = eventsAdapter.removeEvent(position)
        val vm = UnscheduledQuestViewModel(e.name, e.duration, e.backgroundColor)
        unscheduledEventsAdapter.addEvent(vm)
    }

    override fun onMoveEvent(dragView: View, startTime: Time?, endTime: Time?) {
        if (startTime == null) {
            dragView.dragStartTime.visibility = View.GONE
            dragView.dragEndTime.visibility = View.GONE
        } else {
            dragView.dragStartTime.visibility = View.VISIBLE
            dragView.dragEndTime.visibility = View.VISIBLE
            dragView.dragStartTime.text = startTime.toString()
            dragView.dragEndTime.text = endTime.toString()
        }
    }

    override fun onZoomEvent(adapterView: View) {
        eventsAdapter.adaptViewForHeight(adapterView, ViewUtils.pxToDp(adapterView.height, adapterView.context))
    }

    private var actionMode: ActionMode? = null

    private lateinit var eventsAdapter: QuestScheduledEventsAdapter

    private lateinit var unscheduledEventsAdapter: DayViewController.UnscheduledQuestsAdapter

    override fun onContextAvailable(context: Context) {
        Timber.d("Inject")
        inject(iPoliApp.module(context))
    }

    private fun startActionMode() {
        parentController?.view?.startActionMode(object : ActionMode.Callback {
            override fun onActionItemClicked(am: ActionMode, item: MenuItem): Boolean {
                when (item.itemId) {
                    R.id.chooseColor -> {
                        ColorPickerDialogController(object : ColorPickerDialogController.ColorPickedListener {
                            override fun onColorPicked(color: Color) {
                                calendarDayView.updateDragBackgroundColor(color)
                            }

                        }, calendarDayView.getDragViewBackgroundColor())
                            .showDialog(router, "pick_color_tag")
                    }
                }
                return true
            }

            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                actionMode = mode
                mode.menuInflater.inflate(R.menu.calendar_quest_edit_menu, menu)
                return true
            }

            override fun onPrepareActionMode(p0: ActionMode?, p1: Menu?): Boolean {
                return false
            }

            override fun onDestroyActionMode(p0: ActionMode?) {
                actionMode = null
            }
        })
    }

    private fun stopActionMode() {
        actionMode?.finish()
    }

    data class QuestViewModel(override var name: String,
                              override var duration: Int,
                              override var startMinute: Int,
                              val startTime: String,
                              val endTime: String,
                              override var backgroundColor: Color,
                              @ColorRes val textColor: Int,
                              var isCompleted: Boolean) : CalendarEvent

    inner class QuestScheduledEventsAdapter(context: Context, events: List<QuestViewModel>, private val calendarDayView: CalendarDayView) :
        ScheduledEventsAdapter<QuestViewModel>(context, R.layout.item_calendar_quest, events.toMutableList()) {

        override fun bindView(view: View, position: Int) {
            val vm = getItem(position)

            view.setOnLongClickListener {
                calendarDayView.startEventRescheduling(vm)
                false
            }

            view.startTime.text = vm.startTime
            view.endTime.text = vm.endTime

            if (!vm.isCompleted) {
                view.questName.text = vm.name
                view.questName.setTextColor(ContextCompat.getColor(context, vm.textColor))
                view.questBackground.setBackgroundResource(vm.backgroundColor.color500)
                view.questCategoryIndicator.setBackgroundResource(vm.backgroundColor.color500)
            } else {
                val span = SpannableString(vm.name)
                span.setSpan(StrikethroughSpan(), 0, vm.name.length, 0)
                view.questName.text = span
                view.questBackground.setBackgroundResource(R.color.md_grey_500)
                view.questCategoryIndicator.setBackgroundResource(R.color.md_grey_500)
                view.checkBox.isChecked = true
            }

            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(view.questName, 8, 16, 1, TypedValue.COMPLEX_UNIT_SP)

            (view.checkBox as TintableCompoundButton).supportButtonTintList = tintList(vm.backgroundColor.color500)

            view.post {
                adaptViewForHeight(view, ViewUtils.pxToDp(view.height, context))
            }
        }

        override fun rescheduleEvent(position: Int, startTime: Time, duration: Int) {
            val vm = getItem(position)
            events[position] = vm.copy(
                startMinute = startTime.toMinuteOfDay(),
                startTime = startTime.toString(),
                duration = duration,
                endTime = Time.plusMinutes(startTime, duration).toString()
            )
            notifyDataSetChanged()
        }

        override fun adaptViewForHeight(adapterView: View, height: Float) {
            with(adapterView) {
                when {
                    height < 28 -> ViewUtils.hideViews(checkBox, indicatorContainer, startTime, endTime)
                    height < 80 -> {
                        ViewUtils.showViews(startTime, endTime, indicatorContainer)
                        ViewUtils.hideViews(checkBox)
                        if (indicatorContainer.orientation == LinearLayout.VERTICAL) {
                            indicatorContainer.orientation = LinearLayout.HORIZONTAL
                            reverseIndicators(indicatorContainer)
                        }
                    }
                    else -> {
                        ViewUtils.showViews(checkBox, indicatorContainer, startTime, endTime)
                        if (indicatorContainer.orientation == LinearLayout.HORIZONTAL) {
                            indicatorContainer.orientation = LinearLayout.VERTICAL
                            reverseIndicators(indicatorContainer)
                        }
                    }
                }
            }
        }

        private fun reverseIndicators(indicatorContainer: ViewGroup) {
            val indicators = (0 until indicatorContainer.childCount)
                .map { indicatorContainer.getChildAt(it) }.reversed()

            indicatorContainer.removeAllViews()

            indicators.forEach {
                indicatorContainer.addView(it)
            }
        }

        private fun tintList(@ColorRes color: Int) = ContextCompat.getColorStateList(context, color)
    }

    data class UnscheduledQuestViewModel(override var name: String,
                                         override var duration: Int,
                                         override var backgroundColor: Color) : UnscheduledEvent

    inner class UnscheduledQuestsAdapter(private val items: List<UnscheduledQuestViewModel>, calendarDayView: CalendarDayView) :
        UnscheduledEventsAdapter<UnscheduledQuestViewModel>
        (R.layout.unscheduled_quest_item, items.toMutableList(), calendarDayView) {

        override fun ViewHolder.bind(event: UnscheduledQuestViewModel, calendarDayView: CalendarDayView) {
            itemView.name.text = event.name

            (itemView.unscheduledDone as TintableCompoundButton).supportButtonTintList = tintList(event.backgroundColor.color500, itemView.context)
            itemView.setOnLongClickListener {
                calendarDayView.startEventRescheduling(items[adapterPosition])
                true
            }
        }

        private fun tintList(@ColorRes color: Int, context: Context) = ContextCompat.getColorStateList(context, color)
    }
}