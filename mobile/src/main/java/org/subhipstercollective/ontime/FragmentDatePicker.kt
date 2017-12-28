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

import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.widget.DatePicker
import java.util.*

class FragmentDatePicker : DialogFragment(), DatePickerDialog.OnDateSetListener
{
    private lateinit var mArrivalTime: Calendar

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog
    {
        mArrivalTime = (activity as ActivityMain).mArrivalTime

        // Create a new instance of DatePickerDialog and return it
        return DatePickerDialog(activity,
                this,
                mArrivalTime.get(Calendar.YEAR),
                mArrivalTime.get(Calendar.MONTH),
                mArrivalTime.get(Calendar.DAY_OF_MONTH))
    }

    override fun onDateSet(view: DatePicker, year: Int, month: Int, day: Int)
    {
        // Do something with the date chosen by the user
        mArrivalTime.set(Calendar.YEAR, year)
        mArrivalTime.set(Calendar.MONTH, month)
        mArrivalTime.set(Calendar.DAY_OF_MONTH, day)
        (activity as ActivityMain).setTextArriveDate()
    }
}