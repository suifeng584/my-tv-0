package com.lizongying.mytv0.requests

import retrofit2.Call
import retrofit2.http.GET

interface ReleaseService {
    @GET("ijkplayer/version.json")
    fun getRelease(
    ): Call<ReleaseResponse>
}