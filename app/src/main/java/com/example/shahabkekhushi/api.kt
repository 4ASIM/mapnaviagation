package com.example.week7maps


import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.create


object api {
    var API_BASE_URL: String = "https://api.mapbox.com"

    var builder = Retrofit.Builder()
        .baseUrl(API_BASE_URL)
        .addConverterFactory(
            GsonConverterFactory.create()
        ).build().create(IDirection::class.java)
}