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

import com.google.android.gms.location.places.Place
import com.google.android.gms.maps.model.LatLng

/**
 * Created by mqduck on 12/30/17.
 */
class OnTimePlace
{
    val latLng: LatLng
    val name: String

    constructor(latLng: LatLng, name: String)
    {
        this.latLng = latLng
        this.name = name
    }

    constructor(place: Place)
    {
        latLng = place.latLng
        name = place.name.toString()
    }
}