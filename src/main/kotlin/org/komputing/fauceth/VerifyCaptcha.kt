package org.komputing.fauceth

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

// very crude for now
fun verifyCaptcha(response: String, secret: String): Boolean {
    var sb = StringBuilder()
    sb.append("response=")
    sb.append(response)
    sb.append("&secret=")
    sb.append(secret);

    val httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
        .build();

    val request = HttpRequest.newBuilder()
        .uri(URI.create("https://hcaptcha.com/siteverify"))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .timeout(Duration.ofSeconds(10))
        .POST(HttpRequest.BodyPublishers.ofString(sb.toString())).build();

    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    System.out.println(response.body())
    return response.statusCode() == 200 && response.body().contains("\"success\":true")
}