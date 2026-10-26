package io.github.zjs1925879492.rideweather.core

import org.json.JSONObject
import kotlin.math.roundToInt
import kotlin.math.roundToLong

data class SolarEvent(val type:String,val time:Long,val km:Double,val phase:String,val remainingMinutes:Int)

sealed interface WeatherTimelineEntry {
    val km:Double
    data class Stop(val index:Int,override val km:Double):WeatherTimelineEntry
    data class Sun(val event:SolarEvent):WeatherTimelineEntry { override val km get()=event.km }
}
// The website orders events by mileage; a nearby pre-departure event precedes
// the start card, while events at a stop or after arrival follow that stop.
fun weatherTimeline(samples:List<Sample>,events:List<SolarEvent>):List<WeatherTimelineEntry> =
    (samples.mapIndexed { i,s -> WeatherTimelineEntry.Stop(i,s.km) }+events.map { WeatherTimelineEntry.Sun(it) })
        .sortedWith(compareBy<WeatherTimelineEntry> { it.km }.thenBy { if(it is WeatherTimelineEntry.Sun) { if(it.event.phase=="before") -1 else 1 } else 0 })

// Same ETA/solar crossing interpolation as the website; never bridge track gaps
// or a missing forecast sample. Also include events within 30 minutes of the ride.
fun solarRouteEvents(samples:List<Sample>,forecasts:List<JSONObject>,plan:RidePlan,totalKm:Double,nearSeconds:Long=1800):List<SolarEvent> {
    if(samples.isEmpty() || totalKm<=0) return emptyList()
    data class Node(val type:String,val time:Long,val km:Double,val index:Int,val eta:Long)
    val groups=linkedMapOf<String,MutableList<Node>>()
    samples.forEachIndexed { i,sample ->
        val daily=forecasts.getOrNull(i)?.optJSONObject("daily") ?: return@forEachIndexed
        val days=daily.optJSONArray("time") ?: return@forEachIndexed
        for(type in listOf("sunrise","sunset")) {
            val times=daily.optJSONArray(type) ?: continue
            for(j in 0 until days.length()) {
                val time=if(times.isNull(j)) null else times.optDouble(j).takeIf { it.isFinite() && it>0 }?.toLong()
                if(time!=null) groups.getOrPut("$type:${days.optLong(j)}") { mutableListOf() }.add(Node(type,time,sample.km,i,plan.arrival(sample.km,totalKm)))
            }
        }
    }
    val end=plan.arrival(totalKm,totalKm)
    return groups.values.mapNotNull { nodes ->
        var event:SolarEvent?=null
        fun event(node:Node,time:Long,km:Double,phase:String)=SolarEvent(node.type,time,km,phase,((end-time)/60.0).roundToInt().coerceAtLeast(0))
        for((i,a) in nodes.withIndex()) {
            if(a.eta==a.time) { event=event(a,a.time,a.km,"during"); break }
            val b=nodes.getOrNull(i+1) ?: continue
            if(b.index!=a.index+1 || b.km<=a.km || samples[a.index].segment!=samples[b.index].segment) continue
            val da=a.eta-a.time; val db=b.eta-b.time
            if(da<0 && db>=0) {
                val fraction=-da.toDouble()/(db-da)
                event=event(a,(a.eta+(b.eta-a.eta)*fraction).roundToLong(),a.km+(b.km-a.km)*fraction,"during"); break
            }
        }
        if(event==null) {
            val first=nodes.find { it.index==0 }; val last=nodes.find { it.index==samples.lastIndex }
            if(first!=null && first.time<plan.start && plan.start-first.time<=nearSeconds) event=event(first,first.time,0.0,"before")
            else if(last!=null && last.time>end && last.time-end<=nearSeconds) event=event(last,last.time,totalKm,"after")
        }
        event
    }.sortedBy { it.time }
}
