package io.github.legandy.enigmabridge

import android.os.Parcel
import android.os.Parcelable
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
    @SerializedName("servicereference") val sRef: String?,
    @SerializedName("servicename") val sName: String,
    @SerializedName("name") val name: String,
    @SerializedName("begin") val beginTimestamp: Long,
    @SerializedName("end") val endTimestamp: Long,
    @SerializedName("state") val state: Int
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString(),
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readLong(),
        parcel.readLong(),
        parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(sRef)
        parcel.writeString(sName)
        parcel.writeString(name)
        parcel.writeLong(beginTimestamp)
        parcel.writeLong(endTimestamp)
        parcel.writeInt(state)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Timer> {
        override fun createFromParcel(parcel: Parcel): Timer {
            return Timer(parcel)
        }

        override fun newArray(size: Int): Array<Timer?> {
            return arrayOfNulls(size)
        }
    }
}


class EnigmaClient(private val ipAddress: String, private val user: String, private val pass: String) {

    private val client = OkHttpClient()
    private val gson = Gson()

    companion object {
        private const val TAG = "EnigmaClient"
        private const val API_ABOUT = "/api/about"
        private const val API_TIMER_ADD = "/api/timeradd"
        private const val API_TIMER_DELETE = "/api/timerdelete"
        private const val API_BOUQUETS = "/api/bouquets"
        private const val API_GET_SERVICES = "/api/getservices"
        private const val API_TIMER_LIST = "/api/timerlist"
    }

    private fun buildUrl(path: String, query: String? = null): String {
        return if (query.isNullOrEmpty()) {
            "http://$ipAddress$path"
        } else {
            "http://$ipAddress$path?$query"
        }
    }

    suspend fun checkConnection(): Boolean {
        val url = buildUrl(API_ABOUT)
        return executeRequest(url) != null
    }

    suspend fun addTimer(
        title: String,
        sRef: String,
        startTime: Long,
        endTime: Long,
        repeated: Int,
        afterEvent: Int
    ): Boolean {
        val encodedTitle = URLEncoder.encode(title, "UTF-8")
        val encodedSRef = URLEncoder.encode(sRef, "UTF-8")

        Log.d(TAG, "Final times sent to receiver: Start=$startTime, End=$endTime")

        val query = "sRef=$encodedSRef&name=$encodedTitle&begin=$startTime&end=$endTime" +
                "&description=$encodedTitle&justplay=0&repeated=$repeated&afterevent=$afterEvent"
        val url = buildUrl(API_TIMER_ADD, query)
        return executeRequest(url) != null
    }

    suspend fun editTimer(
        originalTimer: Timer,
        newTitle: String,
        newSRef: String,
        newStartTime: Long,
        newEndTime: Long,
        repeated: Int,
        afterEvent: Int
    ): Boolean {
        val deleteSuccess = deleteTimer(originalTimer)
        if (!deleteSuccess) {
            Log.e(TAG, "editTimer failed because the original timer could not be deleted.")
            return false
        }
        return addTimer(newTitle, newSRef, newStartTime, newEndTime, repeated, afterEvent)
    }

    suspend fun deleteTimer(timer: Timer): Boolean {
        val beginTimestamp = timer.beginTimestamp
        val endTimestamp = timer.endTimestamp
        val query: String

        // DEFINITIVE FIX: Implement the fallback deletion method.
        // If the sRef is missing, we send the command without it,
        // relying on the timestamps alone, just like other clients do.
        if (timer.sRef.isNullOrEmpty()) {
            Log.w(TAG, "Timer '${timer.name}' is missing sRef. Using fallback delete method (timestamps only).")
            query = "begin=$beginTimestamp&end=$endTimestamp"
        } else {
            Log.d(TAG, "Using standard delete method (sRef + timestamps) for timer '${timer.name}'.")
            val encodedSRef = URLEncoder.encode(timer.sRef, "UTF-8")
            query = "sRef=$encodedSRef&begin=$beginTimestamp&end=$endTimestamp"
        }

        val url = buildUrl(API_TIMER_DELETE, query)
        Log.d(TAG, "Attempting to delete timer with URL: $url")
        return executeRequest(url) != null
    }

    suspend fun getBouquets(): Map<String, String>? {
        val url = buildUrl(API_BOUQUETS)
        val jsonString = executeRequest(url)
        return if (jsonString != null) {
            try {
                val response = gson.fromJson(jsonString, BouquetResponse::class.java)
                val bouquetMap = response.bouquets.associate { it[1] to it[0] } // Name -> sRef
                Log.d(TAG, "Successfully parsed ${bouquetMap.size} bouquets.")
                bouquetMap
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse bouquets JSON.", e)
                null
            }
        } else {
            Log.e(TAG, "getBouquets: Received null response from executeRequest.")
            null
        }
    }

    suspend fun getChannelsInBouquet(bouquetSref: String): Map<String, String>? {
        val encodedSref = URLEncoder.encode(bouquetSref, "UTF-8")
        val url = buildUrl(API_GET_SERVICES, "sRef=$encodedSref")
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
            Log.e(TAG, "getChannelsInBouquet: Received null response from executeRequest.")
            null
        }
    }

    suspend fun getTimerList(): List<Timer>? {
        val url = buildUrl(API_TIMER_LIST)
        val jsonString = executeRequest(url)
        return if (jsonString != null) {
            try {
                val response = gson.fromJson(jsonString, TimerListResponse::class.java)
                Log.d(TAG, "Successfully parsed ${response.timers.size} timers.")
                response.timers
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse timer list JSON.", e)
                null
            }
        } else {
            Log.e(TAG, "getTimerList: Received null response from executeRequest.")
            null
        }
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
                val responseBody = response.body?.string()
                Log.d(TAG, "Request successful for URL: $url. Response: $responseBody")
                responseBody
            } else {
                Log.e(TAG, "Request failed with code: ${response.code} for URL: $url")
                null
            }
        } catch (e: IOException) {
            Log.e(TAG, "IOException during request for URL: $url", e)
            null
        }
    }
}

