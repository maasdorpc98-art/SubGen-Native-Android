package com.subgen.nativeapp

import android.os.Build
import android.widget.CheckBox

/** Keep visible and accessibility state in sync for user and programmatic changes. */
internal fun CheckBox.bindOnOffLabel(label:String,onChanged:(Boolean)->Unit = {}) {
    fun update(on:Boolean) {
        val state=if(on) "ON" else "OFF"
        text="$label · $state"
        if(Build.VERSION.SDK_INT>=30) stateDescription=state
    }
    update(isChecked)
    setOnCheckedChangeListener{_,on->update(on);onChanged(on)}
}
