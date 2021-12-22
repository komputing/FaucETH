package org.komputing.fauceth

import kotlinx.coroutines.delay
import okhttp3.*
import java.io.IOException
import java.time.Duration

// very crude for now
suspend fun verifyCaptcha(hCaptchaResponse: String, hCaptchaSecret: String): Boolean {

    val client = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(5))
        .readTimeout(Duration.ofSeconds(10))
        .build()

    val request = Request.Builder()
        .url("https://hcaptcha.com/siteverify")
        .header("Content-Type", "application/x-www-form-urlencoded")
        .post(FormBody.Builder().add("response", hCaptchaResponse).add("secret", hCaptchaSecret).build())
        .build()


    var attempts = 0

    while (true) {

        try {
            val response = client.newCall(request).execute()
            if (response.code == 200) {
                return (response.body?.string()?.contains("\"success\":true") == true)
            }
        } catch (ioe: IOException) {
            // can happen - we will retry
        }

        if (attempts == 3) return false

        attempts++
        delay(500L * attempts)
    }

}