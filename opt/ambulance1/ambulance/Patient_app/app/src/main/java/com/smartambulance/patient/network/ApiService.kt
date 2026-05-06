package com.smartambulance.patient.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

data class RequestAmbulancePayload(
    val name: String,
    val age: String,
    val phone: String,
    val lat: Double,
    val lon: Double
)

data class RequestAmbulanceResponse(
    val status: String,
    val emergency_id: String
)

data class EmergencyStatusResponse(
    val status: String,
    val emergency_state: String, 
    val driver_name: String?,
    val ambulance_no: String?,
    val driver_phone: String?,
    val driver_upi: String?,
    val dest_lat: Double?,
    val dest_lon: Double?,
    val dest_name: String?,
    val fare: Double?,
    val lat: Double?,
    val lon: Double?,
    val payment_status: String?
)

data class AmbulanceLocationResponse(
    val lat: Double,
    val lon: Double,
    val distance: String?,
    val eta: String?
)

data class Hospital(
    val name: String,
    val address: String,
    val location: LocationData
)

data class LocationData(
    val lat: Double,
    val lon: Double
)

data class NearbyHospitalsResponse(
    val status: String,
    val hospitals: List<Hospital>
)

data class DriverLoginPayload(val username: String, val password: String)

data class DriverLoginResponse(
    val status: String,
    val driver_id: Int?,
    val driver_name: String?,
    val ambulance_no: String?,
    val message: String?
)

data class DriverRegisterPayload(
    val driver_name: String, val username: String, val password: String,
    val ambulance_no: String, val phone: String, val upi_id: String
)

data class SimpleResponse(
    val status: String,
    val message: String?
)

data class DriverEmergency(
    val emergency_id: String,
    val patient_name: String,
    val patient_age: String?,
    val patient_mobile: String,
    val lat: Double,
    val lon: Double,
    val patient_location: LocationData,
    val distance: Double?
)

data class MyEmergenciesResponse(val emergencies: List<DriverEmergency>)

data class DriverLocationPayload(val driver_id: Int, val location: LocationData)
data class DriverEmergencyActionPayload(val emergency_id: String, val driver_id: Int)
data class HospitalAssignPayload(
    val emergency_id: String, val lat: Double, val lon: Double, val name: String
)

data class MandatePayload(
    val user_id: String,
    val max_limit: Double = 2000.0,
    val mandate_token: String? = null
)

data class MandateResponse(
    val status: String,
    val message: String,
    val token: String?
)

interface ApiService {
    @POST("/send-email-otp")
    suspend fun sendEmailOtp(@Body payload: Map<String, String>): SimpleResponse

    @POST("/verify-email-otp")
    suspend fun verifyEmailOtp(@Body payload: Map<String, String>): SimpleResponse

    @POST("/api/request_ambulance")
    suspend fun requestAmbulance(@Body payload: RequestAmbulancePayload): RequestAmbulanceResponse

    @GET("/api/emergency_status")
    suspend fun getEmergencyStatus(@Query("emergency_id") emergencyId: String): EmergencyStatusResponse

    @GET("/api/ambulance_location")
    suspend fun getAmbulanceLocation(@Query("emergency_id") emergencyId: String): AmbulanceLocationResponse

    @GET("/api/nearby_hospitals")
    suspend fun getNearbyHospitals(@Query("lat") lat: Double, @Query("lon") lon: Double): NearbyHospitalsResponse

    @POST("/login")
    suspend fun loginDriver(@Body payload: DriverLoginPayload): DriverLoginResponse

    @POST("/register_driver")
    suspend fun registerDriver(@Body payload: DriverRegisterPayload): SimpleResponse

    @GET("/api/get_my_emergencies")
    suspend fun getMyEmergencies(@Query("lat") lat: Double, @Query("lon") lon: Double): MyEmergenciesResponse

    @POST("/api/accept_emergency")
    suspend fun acceptEmergency(@Body payload: DriverEmergencyActionPayload): SimpleResponse

    @POST("/api/decline_emergency")
    suspend fun declineEmergency(@Body payload: DriverEmergencyActionPayload): SimpleResponse

    @POST("/api/send_location")
    suspend fun sendLocation(@Body payload: DriverLocationPayload): SimpleResponse

    @POST("/api/patient_picked_up")
    suspend fun patientPickedUp(@Body payload: DriverEmergencyActionPayload): SimpleResponse

    @POST("/api/assign_hospital")
    suspend fun assignHospital(@Body payload: HospitalAssignPayload): SimpleResponse

    @POST("/api/complete_mission")
    suspend fun completeMission(@Body payload: DriverEmergencyActionPayload): SimpleResponse

    @POST("/api/autopay/register_mandate")
    suspend fun registerMandate(@Body payload: MandatePayload): MandateResponse
}

object RetrofitClient {
    var CURRENT_IP = "web-production-67038.up.railway.app"

    private var retrofit: Retrofit? = null
    private var lastIp: String = ""

    val instance: ApiService
        get() {
            if (retrofit == null || lastIp != CURRENT_IP) {
                lastIp = CURRENT_IP
                retrofit = Retrofit.Builder()
                    .baseUrl("https://$CURRENT_IP")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
            }
            return retrofit!!.create(ApiService::class.java)
        }
}
