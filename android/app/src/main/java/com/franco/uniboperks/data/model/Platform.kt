package com.franco.uniboperks.data.model

import com.google.gson.annotations.SerializedName

data class Platform(
    val id: String = "",
    val name: String = "",
    val circuitTag: String = "",
    @SerializedName("student_only")
    val studentOnly: Boolean = true,
    val cost: String = "",
    val verificationMethod: String = "",
    val categories: List<String> = emptyList(),
    val pros: String = "",
    val cons: String = "",
    val url: String = ""
)
