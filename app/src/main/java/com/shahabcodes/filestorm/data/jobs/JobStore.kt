package com.shahabcodes.filestorm.data.jobs

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A saved, re-runnable monthly organize job. */
data class OrganizeJob(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val sources: List<String>,
    val destination: String,
    val move: Boolean,
    val includeSubfolders: Boolean,
    val createdAt: Long,
    val lastRunAt: Long = 0L,
)

object JobStore {
    private lateinit var sp: SharedPreferences

    var jobs by mutableStateOf<List<OrganizeJob>>(emptyList())
        private set

    fun init(context: Context) {
        sp = context.getSharedPreferences("filestorm_jobs", Context.MODE_PRIVATE)
        jobs = runCatching {
            val array = JSONArray(sp.getString("jobs", "[]")!!)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    val sources = mutableListOf<String>()
                    val sArr = o.getJSONArray("sources")
                    for (j in 0 until sArr.length()) sources.add(sArr.getString(j))
                    add(
                        OrganizeJob(
                            id = o.getString("id"),
                            name = o.getString("name"),
                            sources = sources,
                            destination = o.getString("destination"),
                            move = o.getBoolean("move"),
                            includeSubfolders = o.optBoolean("includeSubfolders", false),
                            createdAt = o.getLong("createdAt"),
                            lastRunAt = o.optLong("lastRunAt", 0L),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(job: OrganizeJob) {
        jobs = jobs.filter { it.id != job.id } + job
        persist()
    }

    fun delete(id: String) {
        jobs = jobs.filter { it.id != id }
        persist()
    }

    fun markRun(id: String, at: Long) {
        jobs = jobs.map { if (it.id == id) it.copy(lastRunAt = at) else it }
        persist()
    }

    private fun persist() {
        val array = JSONArray()
        jobs.forEach { job ->
            array.put(
                JSONObject()
                    .put("id", job.id)
                    .put("name", job.name)
                    .put("sources", JSONArray(job.sources))
                    .put("destination", job.destination)
                    .put("move", job.move)
                    .put("includeSubfolders", job.includeSubfolders)
                    .put("createdAt", job.createdAt)
                    .put("lastRunAt", job.lastRunAt)
            )
        }
        sp.edit().putString("jobs", array.toString()).apply()
    }
}
