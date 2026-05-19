package com.external.uxdemo.locationTracker.dto

data class TargetPoint(
    val lat: Double,
    val lon: Double,
    val msl: Double,
    val hDist: Double,
    val rng: Double
)