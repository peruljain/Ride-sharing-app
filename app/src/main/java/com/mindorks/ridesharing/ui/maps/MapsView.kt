package com.mindorks.ridesharing.ui.maps

import com.google.android.gms.maps.model.LatLng


interface MapsView {
    fun showNearByCabs(latlng : List<LatLng>)
    fun informCabBookes()
    fun showPath(latlng : List<LatLng>)
    fun updateCabLocation(latLng: LatLng)
    fun informCabIsArriving()
    fun informcabArrived()
    fun tripStart()
    fun tripEnd()
    fun showRoutesNotAvailableError()
    fun showDirectionApiFailedError(error:String)
}