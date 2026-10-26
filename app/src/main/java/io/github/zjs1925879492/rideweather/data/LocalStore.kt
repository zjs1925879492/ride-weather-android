package io.github.zjs1925879492.rideweather.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.zjs1925879492.rideweather.core.*
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.io.File
import java.util.UUID

class MapKeyStore(context:Context) {
    private val prefs=context.getSharedPreferences("map_credentials",Context.MODE_PRIVATE)
    private fun secret():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("rideweather-map-key",null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("rideweather-map-key",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun get():String = runCatching {
        val encoded=prefs.getString("encrypted",null) ?: return ""
        val iv=Base64.decode(prefs.getString("iv",null),Base64.NO_WRAP)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE,secret(),GCMParameterSpec(128,iv)) }
        cipher.doFinal(Base64.decode(encoded,Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }.getOrDefault("")
    fun save(key:String) {
        if(key.isEmpty()) { check(prefs.edit().clear().commit()) { "无法删除地图 Key" }; return }
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,secret()) }
        check(prefs.edit().putString("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP)).putString("encrypted",Base64.encodeToString(cipher.doFinal(key.toByteArray()),Base64.NO_WRAP)).commit()) { "地图 Key 保存失败" }
    }
}
data class Favorite(val id:String,val name:String,val km:Double,val points:Int,val speed:String,val rest:String,val spacing:String)
class RouteStore(context:Context):SQLiteOpenHelper(context,"routes.db",null,1) {
    private val directory=File(context.filesDir,"routes").apply { mkdirs() }
    override fun onCreate(db:SQLiteDatabase) { db.execSQL("CREATE TABLE routes(id TEXT PRIMARY KEY,name TEXT NOT NULL,gpx TEXT NOT NULL,km REAL NOT NULL,points INTEGER NOT NULL,speed TEXT NOT NULL,rest TEXT NOT NULL,spacing TEXT NOT NULL,updated INTEGER NOT NULL)") }
    override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int) { error("数据库版本不支持") }
    fun list():List<Favorite> = readableDatabase.rawQuery("SELECT id,name,km,points,speed,rest,spacing FROM routes ORDER BY updated DESC",null).use { c -> buildList { while(c.moveToNext()) add(Favorite(c.getString(0),c.getString(1),c.getDouble(2),c.getInt(3),c.getString(4),c.getString(5),c.getString(6))) } }
    fun load(id:String):Route {
        val name=readableDatabase.rawQuery("SELECT gpx FROM routes WHERE id=?",arrayOf(id)).use { c -> require(c.moveToFirst()) { "收藏已不存在" }; c.getString(0) }
        require(Regex("[a-f0-9-]+\\.gpx").matches(name)) { "收藏文件标识无效" }
        return File(directory,name).inputStream().use { Gpx.parse(readBounded(it,MAX_FILE_BYTES).toString(Charsets.UTF_8)) }
    }
    fun save(route:Route,speed:String,rest:String,spacing:String) {
        val db=writableDatabase; val id=route.fingerprint()
        val filename=UUID.randomUUID().toString()+".gpx"
        val newFile=File(directory,filename)
        var previous:String?=null; var committed=false
        db.beginTransaction()
        try {
            val exists=db.rawQuery("SELECT gpx FROM routes WHERE id=?",arrayOf(id)).use { if(it.moveToFirst()) {previous=it.getString(0);true} else false }
            val count=db.rawQuery("SELECT COUNT(*) FROM routes",null).use { it.moveToFirst(); it.getInt(0) }
            require(exists || count<50) { "已收藏 50 条路线，请先删除不需要的收藏" }
            // Keep full GPX outside SQLite rows: large tracks exceed Android's CursorWindow limit.
            val gpx=Gpx.write(route); require(gpx.toByteArray().size<=MAX_FILE_BYTES) { "导出轨迹超过 15 MB" }
            newFile.writeText(gpx)
            val values=ContentValues().apply { put("id",id); put("name",route.name); put("gpx",filename); put("km",route.lengthKm); put("points",route.pointCount); put("speed",speed); put("rest",rest); put("spacing",spacing); put("updated",System.currentTimeMillis()) }
            check(db.insertWithOnConflict("routes",null,values,SQLiteDatabase.CONFLICT_REPLACE)!=-1L) { "收藏保存失败" }
            db.setTransactionSuccessful()
            committed=true
        } finally { db.endTransaction(); if(!committed) newFile.delete() }
        previous?.takeIf { Regex("[a-f0-9-]+\\.gpx").matches(it) }?.let { File(directory,it).delete() }
    }
    fun delete(id:String) {
        val name=readableDatabase.rawQuery("SELECT gpx FROM routes WHERE id=?",arrayOf(id)).use { if(it.moveToFirst()) it.getString(0) else null }
        writableDatabase.delete("routes","id=?",arrayOf(id))
        name?.takeIf { Regex("[a-f0-9-]+\\.gpx").matches(it) }?.let { File(directory,it).delete() }
    }
}
