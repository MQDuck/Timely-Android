package org.subhipstercollective.timely

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import com.google.android.gms.location.places.ui.PlacePicker
import android.widget.Toast
import android.content.Intent
import com.google.android.gms.location.places.PlaceDetectionClient
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.places.GeoDataClient
import com.google.android.gms.location.places.Places

private const val CODE_REQUEST_PLACE_PICKER = 1

class ActivityMain : AppCompatActivity()
{
    private lateinit var mPlaceDetectionClient: PlaceDetectionClient
    private lateinit var mFusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var mGeoDataClient: GeoDataClient

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val builder = PlacePicker.IntentBuilder()

        mGeoDataClient = Places.getGeoDataClient(this, null)
        mPlaceDetectionClient = Places.getPlaceDetectionClient(this, null)
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        startActivityForResult(builder.build(this), CODE_REQUEST_PLACE_PICKER)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent)
    {
        if (requestCode == CODE_REQUEST_PLACE_PICKER)
        {
            if (resultCode == RESULT_OK)
            {
                val place = PlacePicker.getPlace(this, data)
                val toastMsg = String.format("Place: %s", place.name)
                Toast.makeText(this, toastMsg, Toast.LENGTH_LONG).show()
            }
        }
    }
}