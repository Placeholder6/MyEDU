package kg.oshsu.myedu

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object IdDefinitions {

    // In-memory caches
    var countries: Map<Int, DictionaryItem> = emptyMap()
    var oblasts: Map<Int, DictionaryItem> = emptyMap()
    var regions: Map<Int, DictionaryItem> = emptyMap()
    var nationalities: Map<Int, DictionaryItem> = emptyMap()
    var schools: Map<Int, DictionaryItem> = emptyMap()
    var genders: Map<Int, DictionaryItem> = emptyMap()
    var periods: Map<Int, PeriodItem> = emptyMap()
    
    var isLoaded = false

    suspend fun loadAll() = withContext(Dispatchers.IO) {
        try {
            val cList = safeCall { NetworkClient.api.getCountries() }
            countries = cList.associateBy { it.id }

            val oList = safeCall { NetworkClient.api.getOblasts() }
            oblasts = oList.associateBy { it.id }

            val rList = safeCall { NetworkClient.api.getRegions() }
            regions = rList.associateBy { it.id }
            
            val nList = safeCall { NetworkClient.api.getNationalities() }
            nationalities = nList.associateBy { it.id }

            val sList = safeCall { NetworkClient.api.getSchools() }
            schools = sList.associateBy { it.id }
            
            val gList = safeCall { NetworkClient.api.getGenders() }
            genders = gList.associateBy { it.id }
            
            val pList = safeCall { NetworkClient.api.getPeriods() }
            periods = pList.associateBy { it.id }
            
            isLoaded = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun <T> safeCall(call: suspend () -> List<T>): List<T> {
        return try { call() } catch (e: Exception) { emptyList() }
    }

    // Accessors
    fun getCountryName(id: Int?, lang: String = "ru"): String = countries[id]?.getName(lang) ?: id?.toString() ?: "-"
    fun getRegionName(id: Int?, lang: String = "ru"): String = regions[id]?.getName(lang) ?: id?.toString() ?: "-"
    fun getOblastName(id: Int?, lang: String = "ru"): String = oblasts[id]?.getName(lang) ?: id?.toString() ?: "-"
    
    fun getPeriodName(id: Int?, lang: String = "ru"): String {
        val item = periods[id] ?: return id?.toString() ?: "-"
        return when (lang) {
            "ky" -> item.name_kg ?: item.name_ru ?: ""
            "en" -> item.name_en ?: item.name_ru ?: ""
            else -> item.name_ru ?: ""
        }
    }
}