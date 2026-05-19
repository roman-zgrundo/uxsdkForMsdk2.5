package com.external.uxdemo.locationTracker.dto

data class TargetResult(
    val laser: TargetPoint? = null,
    val height: TargetPoint? = null
) {
    // Приоритет выбора: лазер, если нет — высота
    val best: TargetPoint? get() = laser ?: height
}