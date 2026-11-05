package com.franco.uniboperks.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.franco.uniboperks.data.model.Discount
import com.franco.uniboperks.data.model.Platform
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class DiscountsRepository private constructor(private val context: Context) {

    private val gson = Gson()
    private val prefs: SharedPreferences = context.getSharedPreferences("unibo_perks_prefs", Context.MODE_PRIVATE)

    private val _discounts = MutableStateFlow<List<Discount>>(emptyList())
    val discounts: StateFlow<List<Discount>> = _discounts.asStateFlow()

    private val _platforms = MutableStateFlow<List<Platform>>(emptyList())
    val platforms: StateFlow<List<Platform>> = _platforms.asStateFlow()

    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    init {
        loadSavedFavorites()
        loadLocalData()
        // Aggiornamento silenzioso in background all'avvio
        CoroutineScope(Dispatchers.IO).launch {
            syncDiscountsFromGitHub()
        }
    }

    private fun loadSavedFavorites() {
        val saved = prefs.getStringSet("favorites", emptySet()) ?: emptySet()
        _favoriteIds.value = saved
    }

    fun toggleFavorite(discountId: String) {
        val current = _favoriteIds.value.toMutableSet()
        if (current.contains(discountId)) {
            current.remove(discountId)
        } else {
            current.add(discountId)
        }
        _favoriteIds.value = current
        prefs.edit().putStringSet("favorites", current).apply()
    }

    fun isFavorite(discountId: String): Boolean {
        return _favoriteIds.value.contains(discountId)
    }

    private fun loadLocalData() {
        try {
            // Controlla prima se esiste una cache aggiornata scaricata in precedenza
            val cachedFile = File(context.filesDir, "discounts_cached.json")
            val discountsJson = if (cachedFile.exists() && cachedFile.length() > 0) {
                cachedFile.readText()
            } else {
                context.assets.open("discounts.json").bufferedReader().use { it.readText() }
            }

            val discountListType = object : TypeToken<List<Discount>>() {}.type
            val parsedDiscounts: List<Discount> = gson.fromJson(discountsJson, discountListType)
            _discounts.value = parsedDiscounts

            // Carica platforms
            val platformsJson = context.assets.open("platforms.json").bufferedReader().use { it.readText() }
            val platformListType = object : TypeToken<List<Platform>>() {}.type
            val parsedPlatforms: List<Platform> = gson.fromJson(platformsJson, platformListType)
            _platforms.value = parsedPlatforms
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun syncDiscountsFromGitHub(
        urlStr: String = "https://raw.githubusercontent.com/Martiri/unibo-perks/main/data/discounts.json"
    ): Boolean = withContext(Dispatchers.IO) {
        if (_isSyncing.value) return@withContext false
        _isSyncing.value = true
        try {
            val url = URL(urlStr)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 8000
            }

            if (conn.responseCode in 200..299) {
                val remoteJson = conn.inputStream.bufferedReader().use { it.readText() }
                val discountListType = object : TypeToken<List<Discount>>() {}.type
                val parsed: List<Discount> = gson.fromJson(remoteJson, discountListType)

                if (parsed.isNotEmpty()) {
                    // Salva nella cache locale
                    val cachedFile = File(context.filesDir, "discounts_cached.json")
                    cachedFile.writeText(remoteJson)
                    _discounts.value = parsed
                    return@withContext true
                }
            }
            false
        } catch (e: Exception) {
            false
        } finally {
            _isSyncing.value = false
        }
    }

    companion object {
        @Volatile
        private var instance: DiscountsRepository? = null

        fun getInstance(context: Context): DiscountsRepository {
            return instance ?: synchronized(this) {
                instance ?: DiscountsRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
