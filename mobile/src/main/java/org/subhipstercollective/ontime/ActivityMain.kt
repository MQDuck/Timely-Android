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

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.PersistableBundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.CompoundButton
import com.google.android.gms.location.*
import com.google.android.gms.location.places.GeoDataClient
import com.google.android.gms.location.places.PlaceDetectionClient
import com.google.android.gms.location.places.Places
import com.google.android.gms.location.places.ui.PlacePicker
import com.google.android.gms.maps.model.LatLng
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.text.DateFormat
import java.util.*

private const val REQUEST_PLACE_PICKER_ORIGIN = 1
private const val REQUEST_PLACE_PICKER_DESTINATION = 2
private const val PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 3
private const val REQUEST_CHECK_SETTINGS = 4

//private const val KEY_TRACK_LOCATION = "org.subhipstercollective.ontime mTrackLocation"
private const val KEY_LAST_DESTINATION_LATITUDE = "org.subhipstercollective.ontime last destination Latitude"
private const val KEY_LAST_DESTINATION_LONGITUDE = "org.subhipstercollective.ontime last destination Longitude"
private const val KEY_LAST_DESTINATION_NAME = "org.subhipstercollective.ontime last destination name"

private const val MAX_ERROR = 0.02
private const val WAIT_MINIMUM = 90_000L
private const val WAIT_MAXIMUM = 3_600_000L
private const val WAIT_RATE = 0.25
private const val LOCATION_FASTEST_INTERVAL = 60_000L
private const val LOCATION_INTERVAL = 3_000L
private const val MAX_DIRECTIONS_REQUESTS = 10

val Location.latLng: LatLng get() = LatLng(latitude, longitude)

class ActivityMain : AppCompatActivity()
{
    private lateinit var mPlaceDetectionClient: PlaceDetectionClient
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private lateinit var mLocationRequest: LocationRequest
    private lateinit var mLocationCallback: LocationCallback
    private lateinit var mSettingsClient: SettingsClient
    private lateinit var mLocationSettingsRequest: LocationSettingsRequest
    private lateinit var mGeoDataClient: GeoDataClient
    private lateinit var mDateFormatter: DateFormat
    private lateinit var mTimeFormatter: DateFormat
    private var mLocationPermissionGranted = false
    private var mUseCurrentLocation = true
    private var mCurrentLocation: Location? = null
    private var mOrigin: OnTimePlace? = null
    private var mDestination: OnTimePlace? = null
    private var mTrackLocation = false
    var mArrivalTime = Calendar.getInstance()!!
    private var mProjectedDepartureTime = System.currentTimeMillis()
    private var mProjectedDirections: Directions? = null
    private val mHandler = Handler()
    private lateinit var mPreferences: SharedPreferences

    private val runnableUpdateDeparture = Runnable { findDeparture(mArrivalTime.time.time, mProjectedDepartureTime) }

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mGeoDataClient = Places.getGeoDataClient(this, null)
        mPlaceDetectionClient = Places.getPlaceDetectionClient(this, null)
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mTimeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
        mDateFormatter = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault())
        mUseCurrentLocation = toggle_use_current_location.isChecked
        mSettingsClient = LocationServices.getSettingsClient(this);
        mPreferences = getPreferences(Context.MODE_PRIVATE)

        mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
        mLocationRequest.fastestInterval = LOCATION_FASTEST_INTERVAL
        mLocationRequest.interval = LOCATION_INTERVAL

        val builderLocationSettingsRequest = LocationSettingsRequest.Builder()
        builderLocationSettingsRequest.addLocationRequest(mLocationRequest)
        mLocationSettingsRequest = builderLocationSettingsRequest.build()

        text_use_current_location.isEnabled = toggle_use_current_location.isChecked
        layout_origin.visibility = if(toggle_use_current_location.isChecked) View.GONE else View.VISIBLE

        val builderPlacePicker = PlacePicker.IntentBuilder()

        mArrivalTime.set(Calendar.SECOND, 0)

        val destinationName = mPreferences.getString(KEY_LAST_DESTINATION_NAME, "")
        if(destinationName.isNotEmpty())
        {
            val destinationLatitude = mPreferences.getFloat(KEY_LAST_DESTINATION_LATITUDE, 0F)
            val destinationLongitude = mPreferences.getFloat(KEY_LAST_DESTINATION_LONGITUDE, 0F)
            mDestination = OnTimePlace(
                    LatLng(destinationLatitude.toDouble(), destinationLongitude.toDouble()),
                    destinationName)
            text_destination.text = destinationName
        }

        setTextArriveTime()
        setTextArriveDate()

        getLocationPermission()
        getDeviceLocation()

        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult)
            {
                super.onLocationResult(locationResult);

                mCurrentLocation = locationResult.getLastLocation();

                Log.d("foo", "location result")
            }
        }
        button_origin.setOnClickListener({ startActivityForResult(builderPlacePicker.build(this), REQUEST_PLACE_PICKER_ORIGIN) })
        button_destination.setOnClickListener({ startActivityForResult(builderPlacePicker.build(this), REQUEST_PLACE_PICKER_DESTINATION) })
        button_time.setOnClickListener({ FragmentTimePicker().show(supportFragmentManager, "timePicker") })
        button_day.setOnClickListener({ FragmentDatePicker().show(supportFragmentManager, "datePicker") })
        button_departure.setOnClickListener({
            mHandler.removeCallbacks(runnableUpdateDeparture)
            mHandler.post(runnableUpdateDeparture)
            mTrackLocation = mUseCurrentLocation
            startLocationUpdates()
        })
        button_stop.setOnClickListener({
            mHandler.removeCallbacks(runnableUpdateDeparture)
            mTrackLocation = false
            stopLocationUpdates()
        })
        toggle_use_current_location.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            mUseCurrentLocation = isChecked
            text_use_current_location.isEnabled = isChecked
            layout_origin.visibility = if(isChecked) View.GONE else View.VISIBLE
        })
    }

    override fun onResume()
    {
        super.onResume()

        if(mTrackLocation)
        {
            Log.d("foo", "starting location updates")
            startLocationUpdates()
        }
    }

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle)
    {
        Log.d("foo", "onSaveInstanceState")

        val destination = mDestination
        if(destination != null)
        {
            val prefsEditor = mPreferences.edit()
            prefsEditor.putFloat(KEY_LAST_DESTINATION_LATITUDE, destination.latLng.latitude.toFloat())
            prefsEditor.putFloat(KEY_LAST_DESTINATION_LONGITUDE, destination.latLng.longitude.toFloat())
            prefsEditor.putString(KEY_LAST_DESTINATION_NAME, destination.name)
            prefsEditor.apply()
        }

        //outState.putBoolean(KEY_TRACK_LOCATION, mTrackLocation)

        super.onSaveInstanceState(outState, outPersistentState)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
    {
        when(requestCode)
        {
            REQUEST_PLACE_PICKER_ORIGIN ->
            {
                if(data != null)
                {
                    mOrigin = OnTimePlace(PlacePicker.getPlace(this, data))
                    text_origin.text = mOrigin?.name
                }
            }
            REQUEST_PLACE_PICKER_DESTINATION ->
            {
                if(data != null)
                {
                    mDestination = OnTimePlace(PlacePicker.getPlace(this, data))
                    text_destination.text = mDestination?.name
                }
            }
        }
    }

    private fun startLocationUpdates()
    {
        try
        {
            if(mLocationPermissionGranted)
                mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, null)
        }
        catch(e: SecurityException)
        {
            Log.e("Exception: %s", e.message)
        }
    }

    /*private fun startLocationUpdates()
    {
        // Begin by checking if the device has the necessary location settings.
        mSettingsClient.checkLocationSettings(mLocationSettingsRequest)
                .addOnSuccessListener(this) {
                    Log.i("foo", "All location settings are satisfied.")

                    mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                            mLocationCallback, null)
                }
                .addOnFailureListener(this) { e ->
                    val statusCode = (e as ApiException).getStatusCode()
                    when (statusCode)
                    {
                        LocationSettingsStatusCodes.RESOLUTION_REQUIRED         ->
                        {
                            Log.i("foo", "Location settings are not satisfied. Attempting to upgrade " + "location settings ")
                            try
                            {
                                // Show the dialog by calling startResolutionForResult(), and check the
                                // result in onActivityResult().
                                val rae = e as ResolvableApiException
                                rae.startResolutionForResult(this@ActivityMain, REQUEST_CHECK_SETTINGS)
                            }
                            catch (sie: IntentSender.SendIntentException)
                            {
                                Log.i("foo", "PendingIntent unable to execute request.")
                            }

                        }
                        LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE ->
                        {
                            val errorMessage = "Location settings are inadequate, and cannot be " + "fixed here. Fix in Settings."
                            Log.e("foo", errorMessage)
                            Toast.makeText(this@ActivityMain, errorMessage, Toast.LENGTH_LONG).show()
                            mTrackLocation = false
                        }
                    }
                }
    }*/

    private fun stopLocationUpdates()
    {
        mFusedLocationClient.removeLocationUpdates(mLocationCallback)
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
            if(mLocationPermissionGranted)
            {
                val locationResult = mFusedLocationClient.lastLocation
                locationResult.addOnCompleteListener(this) { task ->
                    if (task.isSuccessful)
                    {
                        mCurrentLocation = task.result
                        if(mUseCurrentLocation && task.result != null)
                            mOrigin = OnTimePlace(task.result.latLng, "Current Location")
                    }
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

    private fun findDeparture(arrival: Long = mArrivalTime.time.time, departure: Long = mProjectedDepartureTime)
    {
        getDeviceLocation()

        text_departure.setTextColor(Color.BLACK)

        val origin = mOrigin
        val destination = mDestination

        /*if(mUseCurrentLocation && currentLocation == null)
        {
            startLocationUpdates()
            text_departure.text = "Attempting to get current location..."
            return
        }
        if(!mUseCurrentLocation && chosenOrigin == null)
        {
            text_departure.text = "Please select an origin"
            return
        }
        if(destination == null)
        {
            text_departure.text = "Please select a destination."
            return
        }
        if(arrival < System.currentTimeMillis())
        {
            text_departure.text = "Arrival time must be in the future."
            return
        }

        if(mUseCurrentLocation)
            origin = currentLocation!!.latLng
        else
            origin = chosenOrigin!!.latLng*/

        if(origin == null)
        {
            text_departure.text = "Attempting to get current location..."
            return
        }
        if(destination == null)
        {
            text_departure.text = "Please select a destination."
            return
        }
        if(arrival < System.currentTimeMillis())
        {
            text_departure.text = "Arrival time must be in the future."
            return
        }

        if(mUseCurrentLocation) // should probably do this somewhere else
            startLocationUpdates()

        text_departure.text = "Projecting departure time..."
        doAsync {
            var projectedDirections: Directions? = null
            var projectedDeparture = departure
            if(projectedDeparture < System.currentTimeMillis())
                projectedDeparture = System.currentTimeMillis()
            projectedDeparture /= 1000
            val desiredArrival = arrival / 1000
            var requests = MAX_DIRECTIONS_REQUESTS
            var timesInPast = 0

            do
            {
                if(projectedDeparture < System.currentTimeMillis() / 1000)
                {
                    Log.d("foo", "projected departure is in the past")
                    ++timesInPast
                    if(timesInPast == 2)
                        break
                    projectedDeparture = System.currentTimeMillis()
                }
                projectedDirections = Directions(origin.latLng, destination.latLng, projectedDeparture)
                val projectedDuration = projectedDirections.duration
                val projectedArrival = projectedDeparture + projectedDuration
                val errorAbsolute = desiredArrival - projectedArrival
                val errorRelative = errorAbsolute.toDouble() / projectedDuration.toDouble()
                projectedDeparture += errorAbsolute
                --requests
                Log.d("foo", projectedDirections.getString("url"))
                Log.d("foo", String.format("error: %s  %.2f%%,  duration: %d",
                        errorAbsolute, errorRelative*100, projectedDuration))
            } while(Math.abs(errorRelative) > MAX_ERROR && requests > 0)

            uiThread {
                mProjectedDepartureTime = projectedDeparture * 1000
                mProjectedDirections = projectedDirections
                val departDate = Date(mProjectedDepartureTime)

                if(mProjectedDepartureTime < System.currentTimeMillis())
                    text_departure.setTextColor(Color.RED)
                text_departure.text = "Leave at " + mTimeFormatter.format(departDate) + " " +
                        mDateFormatter.format(departDate)

                val wait = getWait()
                Log.d("foo", "waiting " + Math.round(wait/1000.0) + " seconds")
                mHandler.postDelayed(runnableUpdateDeparture, wait)
            }
        }
    }

    private fun getWait(departure: Long = mProjectedDepartureTime, now: Long = System.currentTimeMillis()): Long
    {
        val wait = ((departure - now) * WAIT_RATE).toLong()
        if(wait < WAIT_MINIMUM)
            return WAIT_MINIMUM
        if(wait > WAIT_MAXIMUM)
            return WAIT_MAXIMUM
        return wait
    }
}
