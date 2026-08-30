package com.openclassrooms.rebonnte.ui.aisle

import java.util.UUID

data class Aisle(
    var name: String,
    val id: String = UUID.randomUUID().toString()
)
