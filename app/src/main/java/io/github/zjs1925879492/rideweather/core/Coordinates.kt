package io.github.zjs1925879492.rideweather.core

import kotlin.math.*

// Port of the WGS84 / GCJ02 / BD09 transformations in gcoord 0.3.2 (MIT).
// See THIRD_PARTY_NOTICES.md. The caller must identify the source coordinate system.
object Coordinates {
    enum class System { WGS84, GCJ02, BD09 }
    fun toWgs84(point: TrackPoint, system: System): TrackPoint {
        if(system==System.WGS84) return point
        var lon=point.lon; var lat=point.lat
        if(system==System.BD09) {
            val x=lon-0.0065; val y=lat-0.006; val xPi=3000*PI/180
            val z=sqrt(x*x+y*y)-0.00002*sin(y*xPi); val theta=atan2(y,x)-0.000003*cos(x*xPi)
            lon=z*cos(theta); lat=z*sin(theta)
        }
        if(lon !in 72.004..137.8347 || lat !in 0.8293..55.8271) return TrackPoint(lat,lon,point.elevation)
        var guessLon=lon; var guessLat=lat
        repeat(30) {
            val delta=offset(guessLon,guessLat)
            val dx=guessLon+delta.first-lon; val dy=guessLat+delta.second-lat
            if(abs(dx)<=1e-6 && abs(dy)<=1e-6) return TrackPoint(guessLat,guessLon,point.elevation)
            guessLon-=dx; guessLat-=dy
        }
        error("坐标转换未收敛")
    }
    private fun offset(lon:Double,lat:Double): Pair<Double,Double> {
        val x=lon-105; val y=lat-35
        var dLon=300+x+2*y+0.1*x*x+0.1*x*y+0.1*sqrt(abs(x))
        dLon+=2*(20*sin(6*x*PI)+20*sin(2*x*PI))/3
        dLon+=2*(20*sin(x*PI)+40*sin(x*PI/3))/3
        dLon+=2*(150*sin(x*PI/12)+300*sin(x*PI/30))/3
        var dLat=2*x-100+3*y+0.2*y*y+0.1*x*y+0.2*sqrt(abs(x))
        dLat+=2*(20*sin(6*x*PI)+20*sin(2*x*PI))/3
        dLat+=2*(20*sin(y*PI)+40*sin(y*PI/3))/3
        dLat+=2*(160*sin(y*PI/12)+320*sin(y*PI/30))/3
        val rad=lat*PI/180; val magic=1-0.006693421622965823*sin(rad).pow(2); val root=sqrt(magic)
        return 180*dLon/(6378245/root*cos(rad)*PI) to 180*dLat/(6378245*(1-0.006693421622965823)/(magic*root)*PI)
    }
}
