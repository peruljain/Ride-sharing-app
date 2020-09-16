package com.mindorks.ridesharing.ui.maps

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.mindorks.ridesharing.R
import com.mindorks.ridesharing.data.network.NetworkService
import com.mindorks.ridesharing.utils.Constants
import com.mindorks.ridesharing.utils.MapUtils
import com.mindorks.ridesharing.utils.PermissionUtils
import com.mindorks.ridesharing.utils.ViewUtils
import kotlinx.android.synthetic.main.activity_maps.*
import org.json.JSONObject

class MapsActivity : AppCompatActivity(), MapsView, OnMapReadyCallback {

    companion object {
        private const val TAG = "MapsActivity"
        private const val LOCATION_PERMISSION_REQUEST_ID = 1999
        private var PICKUP_REQUEST_ID = 6
        private var DROP_REQUEST_ID = 9
    }

    private lateinit var googleMap: GoogleMap
    private lateinit var presenter: MapsPresenter
    private var fusedLocationProviderClient: FusedLocationProviderClient? = null
    private lateinit var locationCallback : LocationCallback
    private var currentLatLng: LatLng?=null
    private val nearByCabListmarker= arrayListOf<Marker>()
    private var pickUpLatlng: LatLng?=null
    private var dropAtLatLng: LatLng?=null
    private var originMarker: Marker? = null
    private var destinationMarker: Marker? = null
    private var movingCabMarker: Marker? = null
    private var blackPolyLine: Polyline? = null
    private var greyPolyLine: Polyline? = null
    private var previousLatLngFromServer:LatLng?=null
    private var currentLatLngFromServer: LatLng?=null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        ViewUtils.enableTransparentStatusBar(window)
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        presenter = MapsPresenter(NetworkService())
        presenter.onAttach(this)
        setUpClickListener()
    }

    private fun AddCarmarker(latlng: LatLng):Marker{
        val bitmapDescriptor= BitmapDescriptorFactory.fromBitmap(MapUtils.getCarBitmap(this))
        return googleMap.addMarker(MarkerOptions().position(latlng).flat(true).icon(bitmapDescriptor))
    }

    private fun enableLocationMap(){
        googleMap.setPadding(0,ViewUtils.dpToPx(48f),0,0)
        googleMap.isMyLocationEnabled=true
    }

    private fun moveCamera(latlng:LatLng?){
        googleMap.moveCamera(CameraUpdateFactory.newLatLng(latlng))
    }

    private fun animateCamera(latlng: LatLng?){
        val cameraPosition = CameraPosition.Builder().target(latlng).zoom(15.5f).build()
        googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
    }

    private fun setUpClickListener() {
        pickUpTextView.setOnClickListener{

            launchlocationAutoComplete(PICKUP_REQUEST_ID)
        }
        dropTextView.setOnClickListener {
            launchlocationAutoComplete(DROP_REQUEST_ID)
        }
        requestCabButton.setOnClickListener{
            statusTextView.visibility=View.VISIBLE
            statusTextView.text=getString(R.string.requesting_your_cab)
            pickUpTextView.isEnabled=false
            dropTextView.isEnabled=false
            requestCabButton.isEnabled=false
            presenter.requestCab(pickUpLatlng!!,dropAtLatLng!!)
        }
        nextRideButton.setOnClickListener {
            reset()
        }

    }

    private fun launchlocationAutoComplete(requestCode: Int) {
        val field: List<Place.Field> = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG)
        val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, field).build(this)
        startActivityForResult(intent, requestCode)
    }

    private fun setUpLocationListener() {
        fusedLocationProviderClient= FusedLocationProviderClient(this)
        val locationRequest = LocationRequest().setInterval(2000).setFastestInterval(2000)
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            locationCallback=object : LocationCallback(){
                override fun onLocationResult(loc: LocationResult) {
                    super.onLocationResult(loc)
                    if(currentLatLng==null){
                        for(location in loc.locations){
                            if(currentLatLng==null){
                                currentLatLng = LatLng(location.latitude,location.longitude)
                                moveCamera(currentLatLng)
                                animateCamera(currentLatLng)
                                enableLocationMap()
                                presenter.requestNearByCabs(currentLatLng!!)
                            }
                        }
                    }
                }
            }
        fusedLocationProviderClient?.requestLocationUpdates(locationRequest,locationCallback,
            Looper.myLooper())
    }

    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap
    }

    override fun onStart() {
        super.onStart()
        when  {
            PermissionUtils.isAccessFineLocationGranted(this) -> {
                when {
                    PermissionUtils.isLocationEnabled(this) -> {
                        //fetch this location
                        setUpLocationListener()
                    }
                    else -> {
                        PermissionUtils.showGPSNOTEnable(this)
                    }
                }
            }
            else ->  {
                PermissionUtils.reqestAccesssFindLocationPermission(this,
                    LOCATION_PERMISSION_REQUEST_ID)
            }
        }
    }

    override fun onDestroy() {
        presenter.onDetach()
        fusedLocationProviderClient?.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode) {
            LOCATION_PERMISSION_REQUEST_ID -> {
                if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    when {
                        PermissionUtils.isLocationEnabled(this) -> {
                            //fetch this location
                            setUpLocationListener()
                        }
                        else -> {
                            PermissionUtils.showGPSNOTEnable(this)
                        }
                    }
                }
                else {
                    Toast.makeText(this, "Location Permission Required", Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if(requestCode== PICKUP_REQUEST_ID || requestCode== DROP_REQUEST_ID){
            //Log.d(TAG, Activity.RESULT_OK.toString());
            when(resultCode){
                Activity.RESULT_OK ->{
                    val place= Autocomplete.getPlaceFromIntent(data!!)
                    when(requestCode){
                        PICKUP_REQUEST_ID->{

                            pickUpTextView.text=place.name
                            pickUpLatlng=place.latLng
                            CheckAndRequestCabButton()
                        }
                        DROP_REQUEST_ID->{
                            dropTextView.text=place.name
                            dropAtLatLng=place.latLng
                            CheckAndRequestCabButton()
                        }
                    }
                }
                AutocompleteActivity.RESULT_ERROR->{
                    val status: Status =Autocomplete.getStatusFromIntent(data!!)
                    //Log.d(TAG, "status$status")
                }
                Activity.RESULT_CANCELED->{

                }
            }
        }
    }

    private fun addOriginDetinationMarkerAndGet(latlng: LatLng): Marker {
        val bitmapDescriptor=BitmapDescriptorFactory.fromBitmap(MapUtils.getDestinationBitmap())
        return googleMap.addMarker(MarkerOptions().position(latlng).flat(true).icon(bitmapDescriptor))
    }

    override fun showNearByCabs(latlngList: List<LatLng>) {
        nearByCabListmarker.forEach { it.remove() }
        nearByCabListmarker.clear()
        requestCabButton.visibility = View.GONE
        statusTextView.text = getString(R.string.your_cab_is_booked)
    }

    override fun informCabBookes() {
        nearByCabListmarker.forEach {
            it.remove()
        }
        nearByCabListmarker.clear()
    }

    override fun showPath(latlnList: List<LatLng>) {
        val builder = LatLngBounds.Builder()
        for (latLng in latlnList) {
            builder.include(latLng)
        }
        val bounds = builder.build()
        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 2))
        val polyLineOptions = PolylineOptions()
        polyLineOptions.color(Color.GRAY)
        polyLineOptions.width(5f)
        polyLineOptions.addAll(latlnList)
        greyPolyLine = googleMap.addPolyline(polyLineOptions)

        val blackpolyLineOptions = PolylineOptions()
        blackpolyLineOptions.color(Color.GRAY)
        blackpolyLineOptions.width(5f)
        blackPolyLine = googleMap.addPolyline(blackpolyLineOptions)

        originMarker = addOriginDetinationMarkerAndGet(latlnList[0])
        originMarker?.setAnchor(0.5f, 0.5f)
        destinationMarker = addOriginDetinationMarkerAndGet(latlnList[latlnList.size - 1])
        destinationMarker?.setAnchor(0.5f,0.5f)
        val polyLineAnimator = com.mindorks.ridesharing.utils.AnimationUtils.polyLineAnimator()
        polyLineAnimator.addUpdateListener { valueAnimator ->
            val percentValue = (valueAnimator.animatedValue as Int)
            val index = (greyPolyLine?.points!!.size) * (percentValue / 100.0f).toInt()
            blackPolyLine?.points = greyPolyLine?.points!!.subList(0, index)
        }
        polyLineAnimator.start()
    }

    override fun updateCabLocation(latLng: LatLng) {
        if(movingCabMarker==null){
            movingCabMarker=AddCarmarker(latLng)
        }
        if(previousLatLngFromServer==null){
            currentLatLngFromServer=latLng
            previousLatLngFromServer=currentLatLngFromServer
            movingCabMarker?.position=currentLatLngFromServer
            movingCabMarker?.setAnchor(0.5f,0.5f)
            animateCamera(currentLatLngFromServer)
        }
        else{
            previousLatLngFromServer=currentLatLngFromServer
            currentLatLngFromServer=latLng
            val valueAnimator=com.mindorks.ridesharing.utils.AnimationUtils.cabAnimator()
            valueAnimator.addUpdateListener {va->
                if(currentLatLngFromServer!=null && previousLatLngFromServer!=null){
                    val multiplier =va.animatedFraction
                    val nextLocation =LatLng(
                        multiplier*currentLatLngFromServer!!.latitude+(1-multiplier)*previousLatLngFromServer!!.latitude,
                        multiplier*currentLatLngFromServer!!.longitude+(1-multiplier)*previousLatLngFromServer!!.longitude
                    )
                    movingCabMarker?.position=nextLocation
                    var rotation=MapUtils.getRotation(previousLatLngFromServer!!,nextLocation)
                    if(!rotation.isNaN()){
                        movingCabMarker?.rotation=rotation
                    }
                    movingCabMarker?.setAnchor(0.5f,0.5f)
                    animateCamera(nextLocation)
                }

            }
            valueAnimator.start()
        }
    }

    override fun informCabIsArriving() {
        statusTextView.text = getString(R.string.your_cab_isArriving)
    }

    override fun informcabArrived() {
        statusTextView.text = getString(R.string.your_cab_arrived)
        greyPolyLine?.remove()
        blackPolyLine?.remove()
        originMarker?.remove()
        destinationMarker?.remove()
    }

    override fun tripStart() {
        statusTextView.text=getString(R.string.trip_started)
        previousLatLngFromServer=null
    }

    override fun tripEnd() {
        statusTextView.text=getString(R.string.trip_finished)
        nextRideButton.visibility=View.VISIBLE
        greyPolyLine?.remove()
        blackPolyLine?.remove()
        originMarker?.remove()
        destinationMarker?.remove()
    }

    override fun showDirectionApiFailedError(error: String) {
        Toast.makeText(this,error,Toast.LENGTH_SHORT).show()
        reset()
    }

    override fun showRoutesNotAvailableError() {
        val error="Route not found"
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
    }


    private fun CheckAndRequestCabButton(){
        if(pickUpLatlng!=null && dropAtLatLng!=null){
            requestCabButton.visibility= View.VISIBLE
            requestCabButton.isEnabled=true
        }
    }

    private fun reset(){
        statusTextView.visibility=View.GONE
        nextRideButton.visibility=View.GONE
        nearByCabListmarker.forEach { it.remove() }
        nearByCabListmarker.clear()
        currentLatLngFromServer=null
        previousLatLngFromServer=null
        if(currentLatLng!=null){
            moveCamera(currentLatLng)
            animateCamera(currentLatLng)
            setCurrentLocationAsPickUP()
            presenter.requestNearByCabs(currentLatLng!!)

        }
        else{
            pickUpTextView.text=""
        }
        pickUpTextView.isEnabled=true
        dropTextView.isEnabled=true
        dropTextView.text=""
        movingCabMarker?.remove()
        blackPolyLine?.remove()
        greyPolyLine?.remove()
        originMarker?.remove()
        destinationMarker?.remove()
        dropAtLatLng=null
        blackPolyLine=null
        greyPolyLine=null
        originMarker=null
        destinationMarker=null
        movingCabMarker=null
    }

    private fun setCurrentLocationAsPickUP(){
        pickUpLatlng=currentLatLng
        pickUpTextView.text=getString(R.string.current_location)
    }

}
