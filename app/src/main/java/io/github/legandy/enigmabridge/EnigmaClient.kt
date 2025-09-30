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

// Updated Timer data class to include more details for the edit screen
data class Timer(
    @SerializedName("servicereference") val sRef: String?,
    @SerializedName("servicename") val sName: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String?,
    @SerializedName("begin") val beginTimestamp: Long,
    @SerializedName("end") val endTimestamp: Long,
    @SerializedName("state") val state: Int,
    @SerializedName("eit") val eit: String?,
    @SerializedName("repeated") val repeated: Int,
    @SerializedName("justplay") val justplay: Int,
    @SerializedName("afterevent") val afterevent: Int
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString(),
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readString(),
        parcel.readLong(),
        parcel.readLong(),
        parcel.readInt(),
        parcel.readString(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(sRef)
        parcel.writeString(sName)
        parcel.writeString(name)
        parcel.writeString(description)
        parcel.writeLong(beginTimestamp)
        parcel.writeLong(endTimestamp)
        parcel.writeInt(state)
        parcel.writeString(eit)
        parcel.writeInt(repeated)
        parcel.writeInt(justplay)
        parcel.writeInt(afterevent)
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
        description: String,
        sRef: String,
        startTime: Long,
        endTime: Long,
        repeated: Int,
        afterEvent: Int,
        justPlay: Int
    ): Boolean {
        val encodedTitle = URLEncoder.encode(title, "UTF-8")
        val encodedDescription = URLEncoder.encode(description, "UTF-8")
        val encodedSRef = URLEncoder.encode(sRef, "UTF-8")

        val query = "sRef=$encodedSRef&name=$encodedTitle&begin=$startTime&end=$endTime" +
                "&description=$encodedDescription&justplay=$justPlay&repeated=$repeated&afterevent=$afterEvent"
        val url = buildUrl(API_TIMER_ADD, query)
        return executeRequest(url) != null
    }

    suspend fun editTimer(
        originalTimer: Timer,
        newTitle: String,
        newDescription: String,
        newSRef: String,
        newStartTime: Long,
        newEndTime: Long,
        repeated: Int,
        afterEvent: Int,
        justPlay: Int
    ): Boolean {
        val deleteSuccess = deleteTimer(originalTimer)
        if (!deleteSuccess) {
            Log.e(TAG, "editTimer failed because the original timer could not be deleted.")
            return false
        }
        return addTimer(newTitle, newDescription, newSRef, newStartTime, newEndTime, repeated, afterEvent, justPlay)
    }

    suspend fun deleteTimer(timer: Timer): Boolean {
        val beginTimestamp = timer.beginTimestamp
        val endTimestamp = timer.endTimestamp
        val query: String

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
                response.bouquets.associate { it[1] to it[0] }
            } catch (e: Exception) {
                null
            }
        } else {
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
                response.services.associate { it.sName to it.sRef }
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    suspend fun getTimerList(): List<Timer>? {
        val url = buildUrl(API_TIMER_LIST)
        val jsonString = executeRequest(url)
        return if (jsonString != null) {
            try {
                val response = gson.fromJson(jsonString, TimerListResponse::class.java)
                response.timers
            } catch (e: Exception) {
                null
            }
        } else {
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
                Log.d(TAG, "Request successful. Response: $responseBody")
                responseBody
            } else {
                Log.e(TAG, "Request failed with code: ${response.code}")
                null
            }
        } catch (e: IOException) {
            Log.e(TAG, "IOException during request", e)
            null
        }
    }
}

