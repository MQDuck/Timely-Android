package org.subhipstercollective.timely

import android.os.Bundle
import android.support.wearable.activity.WearableActivity

class ActivityMain : WearableActivity()
{

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Enables Always-on
        setAmbientEnabled()
    }
}
