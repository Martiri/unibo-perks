package com.franco.uniboperks.data.model

import com.google.gson.annotations.SerializedName

data class Discount(
    val id: String = "",
    val title: String = "",
    val provider: String = "",
    val category: String = "",
    val circuit: String = "",
    val isUniboOfficial: Boolean = false,
    @SerializedName("student_only")
    val studentOnly: Boolean = true,
    val discountBadge: String = "",
    val summary: String = "",
    val reliability: Reliability? = null,
    val eligibility: List<String> = emptyList(),
    val steps: List<String> = emptyList(),
    val url: String = "",
    val expiresAt: String? = null,
    val source: String? = null,
    val updatedAt: String? = null
)

data class Reliability(
    val score: Int = 5,
    val reason: String = ""
)
