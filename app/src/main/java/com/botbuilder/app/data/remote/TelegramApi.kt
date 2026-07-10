package com.botbuilder.app.data.remote

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface TelegramApi {
    @GET("bot{token}/getMe")
    suspend fun getMe(@Path("token") token: String): TgResponse<TgUser>

    @GET("bot{token}/getUpdates")
    suspend fun getUpdates(
        @Path("token") token: String,
        @Query("offset") offset: Long?,
        @Query("timeout") timeout: Int = 30
    ): TgResponse<List<TgUpdate>>

    @POST("bot{token}/sendMessage")
    suspend fun sendMessage(
        @Path("token") token: String,
        @Body body: SendMessageBody
    ): TgResponse<TgMessage>

    companion object {
        // Long-poll timeout (30s) needs a longer client read timeout or every poll will fail.
        fun create(): TelegramApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(40, TimeUnit.SECONDS) // > getUpdates timeout param
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl("https://api.telegram.org/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(TelegramApi::class.java)
        }
    }
}
