package io.ipoli.android.reminder.view.picker

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import io.ipoli.android.R
import io.ipoli.android.common.ViewUtils
import io.ipoli.android.common.di.ControllerModule
import io.ipoli.android.common.mvi.ViewStateRenderer
import io.ipoli.android.common.view.MviDialogController
import io.ipoli.android.common.view.string
import io.ipoli.android.iPoliApp
import kotlinx.android.synthetic.main.dialog_reminder_picker.view.*
import space.traversal.kapsule.Injects
import space.traversal.kapsule.inject
import space.traversal.kapsule.required

typealias TimeUnitConverter = java.util.concurrent.TimeUnit

/**
 * Created by Venelin Valkov <venelin@ipoli.io>
 * on 10/4/17.
 */

enum class TimeUnit(val minutes: Long) {

    MINUTES(1),
    HOURS(60),
    DAYS(TimeUnitConverter.DAYS.toMinutes(1)),
    WEEKS(TimeUnitConverter.DAYS.toMinutes(7))
}

data class ReminderViewModel(val message: String, val minutesFromStart: Long)

class ReminderPickerDialogController :
    MviDialogController<ReminderPickerViewState, ReminderPickerDialogController, ReminderPickerDialogPresenter, ReminderPickerIntent>
    , ViewStateRenderer<ReminderPickerViewState>, Injects<ControllerModule> {

    private var listener: ReminderPickedListener? = null

    private var reminder: ReminderViewModel? = null

    private val presenter by required { reminderPickerPresenter }

    constructor(listener: ReminderPickedListener, selectedReminder: ReminderViewModel? = null) : this() {
        this.listener = listener
        this.reminder = selectedReminder
    }

    constructor(args: Bundle? = null) : super(args)

    override fun createPresenter() = presenter

    override fun render(state: ReminderPickerViewState, view: View) {

        when (state.type) {
            ReminderPickerViewState.StateType.NEW_REMINDER -> {
                ViewUtils.showViews(view.predefinedTimes)
                ViewUtils.hideViews(view.customTimeContainer)
                view.message.setText(state.message)
                view.message.setSelection(state.message.length)

                val predefinedTimesAdapter = ArrayAdapter(activity!!, android.R.layout.simple_spinner_item, state.predefinedValues)
                predefinedTimesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                view.predefinedTimes.adapter = predefinedTimesAdapter
                view.predefinedTimes.setSelection(state.predefinedIndex!!)
            }

            ReminderPickerViewState.StateType.EDIT_REMINDER -> {
                view.message.setText(state.message)
                view.message.setSelection(state.message.length)

                if (state.predefinedIndex != null) {
                    ViewUtils.showViews(view.predefinedTimes)
                    ViewUtils.hideViews(view.customTimeContainer)
                    val predefinedTimesAdapter = ArrayAdapter(activity!!, android.R.layout.simple_spinner_item, state.predefinedValues)
                    predefinedTimesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    view.predefinedTimes.adapter = predefinedTimesAdapter
                    view.predefinedTimes.setSelection(state.predefinedIndex)
                }

                if (state.timeUnitIndex != null) {

                    showCustomTimeViews(view)

                    val customTimeAdapter = ArrayAdapter(activity!!, android.R.layout.simple_spinner_item, state.timeUnits)
                    customTimeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    view.customTimeUnits.adapter = customTimeAdapter
                    view.customTimeUnits.setSelection(state.timeUnitIndex)
                    view.customTime.setText(state.timeValue)
                }
            }

            ReminderPickerViewState.StateType.CUSTOM_TIME -> {
                showCustomTimeViews(view)

                val customTimeAdapter = ArrayAdapter(activity!!, android.R.layout.simple_spinner_item, state.timeUnits)
                customTimeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                view.customTimeUnits.adapter = customTimeAdapter
                view.customTimeUnits.setSelection(state.timeUnitIndex!!)
                view.customTime.setText(state.timeValue)

                view.customTime.requestFocus()
                ViewUtils.showKeyboard(activity!!, view.customTime)
            }

            ReminderPickerViewState.StateType.FINISHED -> {
                listener?.onReminderPicked(state.viewModel!!)
                dismissDialog()
            }

            ReminderPickerViewState.StateType.TIME_VALUE_VALIDATION_ERROR -> {
                view.customTime.error = string(R.string.invalid_reminder_time)
            }
        }
    }

    private fun showCustomTimeViews(dialogView: View) {
        ViewUtils.showViews(dialogView.customTimeContainer)
        ViewUtils.hideViews(dialogView.predefinedTimes)
    }

    override fun onContextAvailable(context: Context) {
        inject(iPoliApp.controllerModule(context, router))
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        send(LoadReminderDataIntent(reminder))
    }

    override fun onCreateDialog(savedViewState: Bundle?): DialogView {

        val contentView = LayoutInflater.from(activity!!).inflate(R.layout.dialog_reminder_picker, null)

        with(contentView) {

            message.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                }

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    send(ChangeMessageIntent(s.toString()))
                }

            })

            predefinedTimes.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {
                }

                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    send(ChangePredefinedTimeIntent(position))
                }
            }

            customTimeUnits.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {
                }

                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    send(ChangeTimeUnitIntent(position))
                }

            }

            customTime.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                }

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    send(ChangeCustomTimeIntent(s.toString()))
                }

            })
        }

        val dialog = AlertDialog.Builder(activity!!)
            .setView(contentView)
            .setTitle(R.string.reminder_dialog_title)
            .setIcon(R.drawable.pet_5_head)
            .setPositiveButton(R.string.dialog_ok, null)
            .setNegativeButton(R.string.cancel, { _, _ -> ViewUtils.hideKeyboard(contentView) })
            .setNeutralButton(R.string.do_not_remind, { _, _ ->
                listener?.onReminderPicked(null)
            })
            .create()

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                send(PickReminderIntent)
            }
        }

        return DialogView(dialog, contentView)
    }

    interface ReminderPickedListener {
        fun onReminderPicked(reminder: ReminderViewModel?)
    }
}