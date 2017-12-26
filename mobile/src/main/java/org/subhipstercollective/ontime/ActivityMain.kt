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
import org.json.JSONObject
import java.net.URL
import java.text.DateFormat
import java.util.*

private const val CODE_REQUEST_PLACE_PICKER = 1
private const val PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 2

private const val MAX_ERROR = 0.01667

val Location.latLng: LatLng get() = LatLng(latitude, longitude)

class ActivityMain : AppCompatActivity()
{
    private lateinit var mPlaceDetectionClient: PlaceDetectionClient
    private lateinit var mFusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var mGeoDataClient: GeoDataClient
    private lateinit var mDateFormatter: DateFormat
    private lateinit var mTimeFormatter: DateFormat
    private var mLocationPermissionGranted = false
    private var mLastKnownLocation: Location? = null
    private var mDestination: Place? = null
    private var mArrivalTime = Date()

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mGeoDataClient = Places.getGeoDataClient(this, null)
        mPlaceDetectionClient = Places.getPlaceDetectionClient(this, null)
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        mDateFormatter = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault())
        mTimeFormatter = DateFormat.getTimeInstance(DateFormat.DEFAULT, Locale.getDefault())

        text_arrive_time.text = mTimeFormatter.format(mArrivalTime)
        text_arrive_date.text = mDateFormatter.format(mArrivalTime)

        val builder = PlacePicker.IntentBuilder()

        getLocationPermission()
        getDeviceLocation()

        button_destination.setOnClickListener({ startActivityForResult(builder.build(this), CODE_REQUEST_PLACE_PICKER) })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent)
    {
        if (requestCode == CODE_REQUEST_PLACE_PICKER)
        {
            if (resultCode == RESULT_OK)
            {
                mDestination = PlacePicker.getPlace(this, data)
                text_destination_name.text = mDestination?.name
                findDeparture(1516037400)
            }
        }
    }

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

    private fun findDeparture(arrival: Long, departure: Long = System.currentTimeMillis() / 1000)
    {
        val origin = mLastKnownLocation
        val destination = mDestination
        if(origin != null && destination != null)
        {
            doAsync {
                var currentDirections: JSONObject
                var currentDeparture = departure
                do
                {
                    currentDirections = queryDirections(origin.latLng, destination.latLng, currentDeparture)
                    val currentDuration = duration(currentDirections)
                    val currentArrival = currentDeparture + currentDuration
                    val errorAbsolute = arrival - currentArrival
                    val errorRelative = errorAbsolute.toDouble() / currentDuration.toDouble()
                    currentDeparture += errorAbsolute
                    //Log.d("foo", "error: " + errorAbsolute + "   " + errorRelative*100 + "%,   " + "duration: " + currentDuration)
                    Log.d("foo", String.format("error: %s  %.2f%%,  duration: %d",
                            errorAbsolute, errorRelative*100, currentDuration))
                } while(Math.abs(errorRelative) > MAX_ERROR)

                uiThread {
                    Log.d("foo", currentDirections.getString("url"))
                    val departDate = Date(currentDeparture * 1000)
                    val departStr = mDateFormatter.format(departDate) + " " + mTimeFormatter.format(departDate)
                    text_hello.text = departStr
                }
            }
        }
    }

    private fun queryDirections(origin: LatLng, destination: LatLng, time: Long): JSONObject
    {
        val url = "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=" + origin.latitude + "," + origin.longitude + "&" +
                "destination=" + destination.latitude + "," + destination.longitude + "&" +
                "departure_time=" + time + "&" +
                "alternatives=false" + "&" +
                "key=AIzaSyDZQhWnsL-wuaG4yuFnA6U7Jx0gujhmPwc"
        val directions = JSONObject(URL(url).readText())
        directions.put("url", url) // only for testing purposes
        return directions
    }

    private fun duration(directions: JSONObject): Long
    {
        val route = directions.getJSONArray("routes")[0] as JSONObject
        val leg = route.getJSONArray("legs")[0] as JSONObject
        return leg.getJSONObject("duration_in_traffic").getLong("value")
    }
}
