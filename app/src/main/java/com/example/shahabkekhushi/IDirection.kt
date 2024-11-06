package com.example.shahabkekhushi

import com.example.week7maps.models.DirectionResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface IDirection {

 @GET("/directions/v5/mapbox/walking/{startLon},{startLat};{endLon},{endLat}")
 suspend fun getDirections
             (@Path("startLat") startLat:String,
              @Path("startLon") startLot:String,
              @Path("endLat") endLat:String,
              @Path("endLon") endLot:String,
              @Query("access_token") token:String,
              @Query("geometries") geo:String = "geojson"
 ): Response<DirectionResponse>
}