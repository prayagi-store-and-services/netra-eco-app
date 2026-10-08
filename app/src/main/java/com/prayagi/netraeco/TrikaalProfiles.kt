package com.prayagi.netraeco

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Device-private profiles. Never submitted to Eco stats, crash messages, or AI. */
data class TrikaalProfile(val name:String, val date:String, val time:String, val zone:String,
    val latitude:String, val longitude:String, val place:String, val offset:String)
object TrikaalProfiles {
    private const val PREFS="trikaal_local_profiles"
    fun read(context:Context):List<TrikaalProfile> = try {
        val a=JSONArray(context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("profiles","[]"))
        (0 until a.length()).map { i -> val o=a.getJSONObject(i); TrikaalProfile(o.optString("name"),o.optString("date"),o.optString("time"),o.optString("zone"),o.optString("lat"),o.optString("lon"),o.optString("place"),o.optString("offset")) }
    } catch(e:Exception) { emptyList() }
    fun save(context:Context, p:TrikaalProfile):Boolean {
        val profiles=(read(context).filterNot{it==p}+p).takeLast(20)
        val a=JSONArray(); profiles.forEach { a.put(JSONObject().put("name",it.name).put("date",it.date).put("time",it.time).put("zone",it.zone).put("lat",it.latitude).put("lon",it.longitude).put("place",it.place).put("offset",it.offset)) }
        return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("profiles",a.toString()).commit()
    }
    fun delete(context:Context,p:TrikaalProfile):Boolean {
        val a=JSONArray();read(context).filterNot{it==p}.forEach{a.put(JSONObject().put("name",it.name).put("date",it.date).put("time",it.time).put("zone",it.zone).put("lat",it.latitude).put("lon",it.longitude).put("place",it.place).put("offset",it.offset))}
        return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("profiles",a.toString()).commit()
    }
}
