package io.github.legandy.enigmabridge

import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder

class EnigmaClient(private val ipAddress: String, private val user: String, private val pass: String) {

    private val client = OkHttpClient()

    // 'suspend' marks this as a function that can be paused for long-running work
    suspend fun checkConnection(): Boolean {
        // A simple API call that returns device info if successful
        val url = "http://$ipAddress/api/about"

        val requestBuilder = Request.Builder().url(url)
        if (user.isNotEmpty() || pass.isNotEmpty()) {
            val credential = Credentials.basic(user, pass)
            requestBuilder.header("Authorization", credential)
        }

        return try {
            val response = client.newCall(requestBuilder.build()).execute()
            // If the server responds, the connection is successful
            response.isSuccessful
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }
    suspend fun addTimer(title: String, sRef: String, startTime: Long, endTime: Long): Boolean {
        // Enigma2 requires URL-encoded parameters
        val encodedTitle = URLEncoder.encode(title, "UTF-8")
        val encodedSRef = URLEncoder.encode(sRef, "UTF-8")

        val url = "http://$ipAddress/api/timeradd?sRef=$encodedSRef&name=$encodedTitle&begin=$startTime&end=$endTime&description=$encodedTitle&justplay=0&afterevent=0"

        val requestBuilder = Request.Builder().url(url)

        // Add username/password if they are provided
        if (user.isNotEmpty() || pass.isNotEmpty()) {
            val credential = Credentials.basic(user, pass)
            requestBuilder.header("Authorization", credential)
        }

        val request = requestBuilder.build()

        return try {
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }
}