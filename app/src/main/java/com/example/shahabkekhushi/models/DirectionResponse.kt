package com.example.week7maps.models

data class DirectionResponse(
    val code: String,
    val routes: List<Route>,
    val uuid: String,
    val waypoints: List<Waypoint>
)