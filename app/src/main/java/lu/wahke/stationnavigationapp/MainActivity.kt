package lu.wahke.stationnavigationapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.bonuspack.routing.RoadManager
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var stations: List<Station>
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchView: SearchView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var myLocationButton: ImageButton
    private val LOCATION_PERMISSION_REQUEST_CODE = 1000
    private var userLocation: GeoPoint? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // OSMDroid initialization
        Configuration.getInstance().load(this, applicationContext.getSharedPreferences("osmdroid", MODE_PRIVATE))

        mapView = findViewById(R.id.map)
        mapView.setBuiltInZoomControls(true)
        mapView.setMultiTouchControls(true)

        // Initialize FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize the station list
        stations = listOf()

        // Initialize RecyclerView and SearchView
        recyclerView = findViewById(R.id.categoryRecyclerView)
        searchView = findViewById(R.id.searchView)

        // Set layout for RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Initialize the button for current location
        myLocationButton = findViewById(R.id.myLocationButton)
        myLocationButton.setOnClickListener {
            checkLocationPermission()
        }

        // Fetch stations from the API and place markers
        fetchStationsFromApi()

        // Set up the search function
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterStations(newText ?: "")
                return true
            }
        })

        // Check location permissions and show current location
        checkLocationPermission()
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            getCurrentLocation()
        }
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                userLocation = GeoPoint(location.latitude, location.longitude)
                mapView.controller.setCenter(userLocation)
                mapView.controller.setZoom(15.0)

                // Add marker for current location
                val marker = Marker(mapView)
                marker.position = userLocation
                marker.title = "Current Location"
                mapView.overlays.add(marker)
            }
        }
    }

    private fun fetchStationsFromApi() {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://maps.wahke.lu/api/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val apiService = retrofit.create(ApiService::class.java)
        apiService.getStations().enqueue(object : Callback<List<Station>> {
            override fun onResponse(call: Call<List<Station>>, response: Response<List<Station>>) {
                if (response.isSuccessful) {
                    stations = response.body() ?: listOf()
                    displayStationsOnMap(stations)
                }
            }

            override fun onFailure(call: Call<List<Station>>, t: Throwable) {
                // Error handling
            }
        })
    }

    private fun displayStationsOnMap(stations: List<Station>) {
        for (station in stations) {
            val marker = Marker(mapView)
            marker.position = GeoPoint(station.latitude.toDouble(), station.longitude.toDouble())
            marker.title = station.name
            mapView.overlays.add(marker)
        }
    }

    private fun filterStations(query: String) {
        // Filter by both name and U-Nummer
        val filteredStations = stations.filter {
            it.name.contains(query, ignoreCase = true) || it.u_nummer.contains(query, ignoreCase = true)
        }

        if (filteredStations.isNotEmpty()) {
            val station = filteredStations.first()
            val stationLocation = GeoPoint(station.latitude.toDouble(), station.longitude.toDouble())

            mapView.controller.setCenter(stationLocation)
            mapView.controller.setZoom(15.0)

            // Add marker for the found station
            val marker = Marker(mapView)
            marker.position = stationLocation
            marker.title = station.name
            mapView.overlays.clear() // Clear previous markers
            mapView.overlays.add(marker)

            // Navigate to the station
            userLocation?.let {
                navigateToStation(it, stationLocation)
            }
        }
    }

    private fun navigateToStation(start: GeoPoint, destination: GeoPoint) {
        val roadManager: RoadManager = OSRMRoadManager(this, "YourUserAgent")
        val waypoints = arrayListOf<GeoPoint>()
        waypoints.add(start)
        waypoints.add(destination)

        // Fetch road/route
        val road = roadManager.getRoad(waypoints)
        if (road.mStatus != Road.STATUS_OK) {
            // Handle error if needed
        }

        // Draw the road on the map
        val roadOverlay = RoadManager.buildRoadOverlay(road)
        mapView.overlays.add(roadOverlay)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation()
            }
        }
    }

    // Retrofit API Interface
    interface ApiService {
        @GET("get_stations.php")
        fun getStations(): Call<List<Station>>
    }

    // Data class for stations
    data class Station(
        val id: String,
        val u_nummer: String,
        val name: String,
        val latitude: String,
        val longitude: String,
        val equipe: String
    )
}
