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

import com.google.android.gms.maps.model.LatLng
import org.json.JSONObject
import java.net.URL

private fun constructUrl(origin: LatLng, destination: LatLng, time: Long) =
        "https://maps.googleapis.com/maps/api/directions/json?" +
        "origin=" + origin.latitude + "," + origin.longitude + "&" +
        "destination=" + destination.latitude + "," + destination.longitude + "&" +
        "departure_time=" + time + "&" +
        "alternatives=false" + "&" +
        "key=AIzaSyDZQhWnsL-wuaG4yuFnA6U7Jx0gujhmPwc"

private fun constructAndReadUrl(origin: LatLng, destination: LatLng, time: Long) =
        URL(constructUrl(origin, destination, time)).readText()

class Directions(origin: LatLng, destination: LatLng, time: Long)
    : JSONObject(constructAndReadUrl(origin, destination, time))
{
    init { put("url", constructUrl(origin, destination, time)) }

    val duration: Long get()
    {
        val route = getJSONArray("routes")[0] as JSONObject
        val leg = route.getJSONArray("legs")[0] as JSONObject
        return leg.getJSONObject("duration_in_traffic").getLong("value")
    }
}