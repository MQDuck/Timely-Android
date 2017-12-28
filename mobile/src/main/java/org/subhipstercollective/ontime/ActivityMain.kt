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

import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.places.GeoDataClient
import com.google.android.gms.location.places.Place
import com.google.android.gms.location.places.PlaceDetectionClient
import com.google.android.gms.location.places.Places
import com.google.android.gms.location.places.ui.PlacePicker
import com.google.android.gms.maps.model.LatLng
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.text.DateFormat
import java.util.*

private const val CODE_REQUEST_PLACE_PICKER = 1
private const val PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 2

private const val MAX_ERROR = 0.02
private const val WAIT_MINIMUM: Long = 90_000
private const val WAIT_MAXIMUM: Long = 3_600_000
private const val WATE_RATE = 0.15

val Location.latLng: LatLng get() = LatLng(latitude, longitude)

class ActivityMain : AppCompatActivity()
{
    private lateinit var mPlaceDetectionClient: PlaceDetectionClient
    private lateinit var mFusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var mGeoDataClient: GeoDataClient
    lateinit var mDateFormatter: DateFormat
    lateinit var mTimeFormatter: DateFormat
    private var mLocationPermissionGranted = false
    private var mLastKnownLocation: Location? = null
    private var mDestination: Place? = null
    var mArrivalTime = Calendar.getInstance()!!
    var mProjectedDepartureTime = System.currentTimeMillis()
    var mProjectedDirections: Directions? = null
    private val mHandler = Handler()

    private val runnableUpdateDeparture = Runnable { findDeparture(mArrivalTime.time.time, mProjectedDepartureTime) }

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mGeoDataClient = Places.getGeoDataClient(this, null)
        mPlaceDetectionClient = Places.getPlaceDetectionClient(this, null)
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        mTimeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
        mDateFormatter = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault())

        mArrivalTime.set(Calendar.SECOND, 0)

        setTextArriveTime()
        setTextArriveDate()

        val builder = PlacePicker.IntentBuilder()

        getLocationPermission()
        getDeviceLocation()

        button_destination.setOnClickListener({ startActivityForResult(builder.build(this), CODE_REQUEST_PLACE_PICKER) })
        button_time.setOnClickListener({ FragmentTimePicker().show(supportFragmentManager, "timePicker") })
        button_day.setOnClickListener({ FragmentDatePicker().show(supportFragmentManager, "datePicker") })
        button_departure.setOnClickListener({
            mHandler.removeCallbacks(runnableUpdateDeparture)
            mHandler.post(runnableUpdateDeparture)
        })
        button_stop.setOnClickListener({ mHandler.removeCallbacks(runnableUpdateDeparture) })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent)
    {
        if (requestCode == CODE_REQUEST_PLACE_PICKER)
        {
            if (resultCode == RESULT_OK)
            {
                mDestination = PlacePicker.getPlace(this, data)
                text_destination_name.text = mDestination?.name
            }
        }
    }

    fun setTextArriveTime() { text_arrive_time.text = mTimeFormatter.format(mArrivalTime.time) }
    fun setTextArriveDate() { text_arrive_date.text = mDateFormatter.format(mArrivalTime.time) }

    private fun getLocationPermission()
    {
        if (ContextCompat.checkSelfPermission(this.applicationContext,
                android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            mLocationPermissionGranted = true
        else
            ActivityCompat.requestPermissions(this,
                    arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray)
    {
        mLocationPermissionGranted = false
        when (requestCode)
        {
            PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION ->
                // If request is cancelled, the result arrays are empty.
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    mLocationPermissionGranted = true
        }
    }

    private fun getDeviceLocation()
    {
        /*
         * Get the best and most recent location of the device, which may be null in rare
         * cases when a location is not available.
         */
        try
        {
            if (mLocationPermissionGranted)
            {
                val locationResult = mFusedLocationProviderClient.lastLocation
                locationResult.addOnCompleteListener(this) { task ->
                    if (task.isSuccessful)
                        mLastKnownLocation = task.result
                    else
                    {
                        Log.d("foo", "Current location is null. Using defaults.")
                        Log.e("foo", "Exception: %s", task.exception)
                    }
                }
            }
        }
        catch (e: SecurityException)
        {
            Log.e("Exception: %s", e.message)
        }

    }

    private fun findDeparture(arrival: Long, departure: Long = mProjectedDepartureTime)
    {
        if(mDestination == null)
        {
            text_departure.text = "Please select a destination."
            return
        }
        if(arrival < Date().time)
        {
            text_departure.text = "Arrival time must be in the future."
            return
        }

        val origin = mLastKnownLocation
        val destination = mDestination
        if(origin != null && destination != null)
        {
            text_departure.text = "Finding projected arrival time..."
            doAsync {
                var currentDirections: Directions
                var currentDeparture = departure / 1000
                val desiredArrival = arrival / 1000
                do
                {
                    currentDirections = Directions(origin.latLng, destination.latLng, currentDeparture)
                    val currentDuration = currentDirections.duration
                    val currentArrival = currentDeparture + currentDuration
                    val errorAbsolute = desiredArrival - currentArrival
                    val errorRelative = errorAbsolute.toDouble() / currentDuration.toDouble()
                    currentDeparture += errorAbsolute
                    Log.d("foo", currentDirections.getString("url"))
                    Log.d("foo", String.format("error: %s  %.2f%%,  duration: %d",
                            errorAbsolute, errorRelative*100, currentDuration))
                } while(Math.abs(errorRelative) > MAX_ERROR)

                uiThread {
                    mProjectedDepartureTime = currentDeparture * 1000
                    mProjectedDirections = currentDirections
                    val departDate = Date(mProjectedDepartureTime)
                    text_departure.text = "Leave at " + mTimeFormatter.format(departDate) + " " +
                            mDateFormatter.format(departDate)

                    val wait = getWait()
                    Log.d("foo", "waiting " + wait/1000 + " seconds")
                    mHandler.postDelayed(runnableUpdateDeparture, wait)
                }
            }
        }
    }

    private fun getWait(departure: Long = mProjectedDepartureTime, now: Long = System.currentTimeMillis()): Long
    {
        val wait = ((departure - now) * WATE_RATE).toLong()
        if(wait < WAIT_MINIMUM)
            return WAIT_MINIMUM
        if(wait > WAIT_MAXIMUM)
            return WAIT_MAXIMUM
        return wait
    }
}
