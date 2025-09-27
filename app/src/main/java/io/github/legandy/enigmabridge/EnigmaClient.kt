package io.github.legandy.enigmabridge

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder

// --- Data classes for JSON parsing ---
data class BouquetResponse(@SerializedName("bouquets") val bouquets: List<List<String>>)
data class ServiceResponse(@SerializedName("services") val services: List<Service>)
data class Service(@SerializedName("servicereference") val sRef: String, @SerializedName("servicename") val sName: String)
data class TimerListResponse(@SerializedName("timers") val timers: List<Timer>)
data class Timer(
    @SerializedName("servicereference") val sRef: String,
    @SerializedName("servicename") val sName: String,
    @SerializedName("name") val name: String,
    @SerializedName("begin") val beginTimestamp: Long,
    @SerializedName("end") val endTimestamp: Long,
    @SerializedName("state") val state: Int
)

class EnigmaClient(private val ipAddress: String, private val user: String, private val pass: String) {

    private val client = OkHttpClient()
    private val gson = Gson()

    companion object {
        private const val TAG = "EnigmaClient"
    }

    suspend fun checkConnection(): Boolean {
        val url = "http://$ipAddress/api/about"
        return executeRequest(url) != null
    }

    suspend fun addTimer(title: String, sRef: String, startTime: Long, endTime: Long): Boolean {
        val encodedTitle = URLEncoder.encode(title, "UTF-8")
        val encodedSRef = URLEncoder.encode(sRef, "UTF-8")
        val url = "http://$ipAddress/api/timeradd?sRef=$encodedSRef&name=$encodedTitle&begin=$startTime&end=$endTime&description=$encodedTitle&justplay=0&afterevent=0"
        return executeRequest(url) != null
    }

    suspend fun getBouquets(): Map<String, String>? {
        val jsonString = executeRequest("http://$ipAddress/api/bouquets")
        return if (jsonString != null) {
            try {
                val response = gson.fromJson(jsonString, BouquetResponse::class.java)
                val bouquetMap = response.bouquets.associate { it[1] to it[0] }
                Log.d(TAG, "Successfully parsed ${bouquetMap.size} bouquets.")
                bouquetMap
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse bouquets JSON.", e)
                null
            }
        } else {
            null
        }
    }

    suspend fun getChannelsInBouquet(bouquetSref: String): Map<String, String>? {
        val encodedSref = URLEncoder.encode(bouquetSref, "UTF-8")
        val url = "http://$ipAddress/api/getservices?sRef=$encodedSref"
        val jsonString = executeRequest(url)
        return if (jsonString != null) {
            try {
                val response = gson.fromJson(jsonString, ServiceResponse::class.java)
                val serviceMap = response.services.associate { it.sName to it.sRef }
                Log.d(TAG, "Successfully parsed ${serviceMap.size} services in bouquet.")
                serviceMap
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse channels JSON.", e)
                null
            }
        } else {
            null
        }
    }

    suspend fun getTimerList(): List<Timer>? {
        val jsonString = executeRequest("http://$ipAddress/api/timerlist")
        return if (jsonString != null) {
            try {
                gson.fromJson(jsonString, TimerListResponse::class.java).timers
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse timer list JSON.", e)
                null
            }
        } else {
            null
        }
    }

    suspend fun deleteTimer(timer: Timer): Boolean {
        val encodedSRef = URLEncoder.encode(timer.sRef, "UTF-8")
        val url = "http://$ipAddress/api/timerdelete?sRef=${encodedSRef}&begin=${timer.beginTimestamp}&end=${timer.endTimestamp}"
        return executeRequest(url) != null
    }

    private fun executeRequest(url: String): String? {
        Log.d(TAG, "Executing request to: $url")
        val requestBuilder = Request.Builder().url(url)
        if (user.isNotEmpty() || pass.isNotEmpty()) {
            val credential = Credentials.basic(user, pass)
            requestBuilder.header("Authorization", credential)
        }
        return try {
            val response = client.newCall(requestBuilder.build()).execute()
            if (response.isSuccessful) {
                response.body?.string().also { Log.d(TAG, "Raw response: $it") }
            } else {
                Log.e(TAG, "Request failed with code: ${response.code}")
                null
            }
        } catch (e: IOException) {
            Log.e(TAG, "IOException during request.", e)
            null
        }
    }
}

