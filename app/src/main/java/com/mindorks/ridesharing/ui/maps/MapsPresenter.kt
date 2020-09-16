package com.mindorks.ridesharing.ui.maps

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.gson.JsonObject
import com.mindorks.ridesharing.data.network.NetworkService
import com.mindorks.ridesharing.simulator.WebSocket
import com.mindorks.ridesharing.simulator.WebSocketListener
import com.mindorks.ridesharing.utils.Constants
import org.json.JSONObject

class MapsPresenter(private val networkService: NetworkService) : WebSocketListener {

    companion object {
        private const val TAG = "MapsPresenter"
    }

    private var view: MapsView? = null
    private lateinit var webSocket: WebSocket

    fun requestNearByCabs(latlng: LatLng){
        val jsonObject= JSONObject()
        jsonObject.put(Constants.TYPE,Constants.NEAR_BY_CABS)
        jsonObject.put(Constants.LAT,latlng.latitude)
        jsonObject.put(Constants.LNG,latlng.longitude)
        webSocket.sendMessage(jsonObject.toString())
    }

    private fun getNearByCabs(jsonObject: JSONObject) {
        var nearBycabLocations= arrayListOf<LatLng>()
        val jsonArray=jsonObject.getJSONArray(Constants.LOCATIONS)

        for (i in 0 until jsonArray.length()){
            var lat=(jsonArray.get(i) as JSONObject).getDouble(Constants.LAT)
            var lng=(jsonArray.get(i) as JSONObject).getDouble(Constants.LNG)
            var latlng= LatLng(lat,lng)
            nearBycabLocations.add(latlng)
        }
        view?.showNearByCabs(nearBycabLocations)

    }

    fun requestCab(pickupLatLng:LatLng,dropLatLng: LatLng){
        val jsonObject= JSONObject()
        jsonObject.put(Constants.TYPE,Constants.REQUEST_CABS)
        jsonObject.put("pickUpLat",pickupLatLng.latitude)
        jsonObject.put("pickUpLng",pickupLatLng.longitude)
        jsonObject.put("dropLat",dropLatLng.latitude)
        jsonObject.put("dropLng",dropLatLng.longitude)
        webSocket.sendMessage(jsonObject.toString())
    }

    fun onAttach(view: MapsView) {
        this.view = view
        webSocket = networkService.createWebSocket(this)
        webSocket.connect()
    }

    fun onDetach() {
        webSocket.disconnect()
        this.view = null
    }

    override fun onConnect() {
        Log.d(TAG, "On Connect")
    }

    override fun onMessage(data: String) {
        Log.d(TAG, "data")
        val jsonObject = JSONObject(data)
        when(jsonObject.getString(Constants.TYPE)) {
            Constants.NEAR_BY_CABS -> {
                getNearByCabs(jsonObject)
            }
            Constants.CAB_BOOKED -> {
                view?.informCabBookes()
            }
            Constants.PICK_UP_PATH -> {
                val jsonArray = jsonObject.getJSONArray("path")
                val pickUpPath = arrayListOf<LatLng>()
                for (i in 0 until jsonArray.length()) {
                    val lat = (jsonArray.get(i) as JSONObject).getDouble(Constants.LAT)
                    val lng = (jsonArray.get(i) as JSONObject).getDouble(Constants.LNG)
                    val latLng = LatLng(lat, lng)
                    pickUpPath.add(latLng)
                }
            }
            Constants.LOCATION->{
                val latCurrent=jsonObject.getDouble("lat")
                val lngCurrent=jsonObject.getDouble("lng")
                view?.updateCabLocation(LatLng(latCurrent,lngCurrent))
            }
            Constants.CAB_IS_ARRIVING->{
                view?.informCabIsArriving()
            }
            Constants.CAB_ARRIVED->{
                view?.informcabArrived()
            }
            Constants.TRIP_START->{
                view?.tripStart()
            }
            Constants.TRIP_END->{
                view?.tripEnd()
            }
        }
    }

    override fun onDisconnect() {
        Log.d(TAG, "On Disconnect")
    }

    override fun onError(error: String) {
        val jsonObject = JSONObject(error)
        when (jsonObject.getString(Constants.TYPE)) {
            Constants.ROUTES_NOT_ENABLED -> {
                view?.showRoutesNotAvailableError()
            }
            Constants.DIRECTION_API_NOT_WORKING -> {
                view?.showDirectionApiFailedError(
                    "Direction API Failed : " + jsonObject.getString(
                        Constants.ERROR
                    )
                )
            }
        }
    }
}