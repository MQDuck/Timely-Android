/*
 * Copyright 2017 Jeffrey Thomas Piercy
 *
 * This file is part of OnTime-Android.
 *
 * OnTime-Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * OnTime-Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OnTime-Android.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.subhipstercollective.ontime

import android.app.Dialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.text.format.DateFormat.is24HourFormat
import android.widget.TimePicker
import java.util.*

class FragmentTimePicker : DialogFragment(), TimePickerDialog.OnTimeSetListener
{
    private lateinit var mArrivalTime: Calendar

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog
    {
        mArrivalTime = (activity as ActivityMain).mArrivalTime

        // Create a new instance of TimePickerDialog and return it
        return TimePickerDialog(activity,
                this,
                mArrivalTime.get(Calendar.HOUR_OF_DAY),
                mArrivalTime.get(Calendar.MINUTE),
                is24HourFormat(activity))
    }

    override fun onTimeSet(view: TimePicker, hourOfDay: Int, minute: Int)
    {
        // Do something with the time chosen by the user
        mArrivalTime.set(Calendar.HOUR_OF_DAY, hourOfDay)
        mArrivalTime.set(Calendar.MINUTE, minute)
        (activity as ActivityMain).saveArrivalDepartureTimes()
        (activity as ActivityMain).updateUI()
    }
}