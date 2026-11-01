package io.github.jabato01.busmetrobarcelona.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

/** A favourite stop: a bus stop (optionally one line) or a metro station. */
sealed interface Favorite {
    val name: String
    val key: String
}

data class BusFavorite(val stop: String, val line: String, override val name: String) : Favorite {
    override val key get() = "bus:$stop:$line"
}

data class MetroFavorite(
    val line: String, val lineName: String, val color: String, val station: String, override val name: String,
) : Favorite {
    override val key get() = "metro:$station"
}

/** Settings in the app's private storage; the TMB keys are also encrypted with an Android Keystore key. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var credentials: Credentials
        get() = Credentials(secret("appId"), secret("appKey"))
        set(value) {
            putSecret("appId", value.appId)
            putSecret("appKey", value.appKey)
        }

    var geocoder: String
        get() = prefs.getString("geocoder", "auto") ?: "auto"
        set(value) = prefs.edit().putString("geocoder", value).apply()

    var favorites: List<Favorite>
        get() = parseFavorites(prefs.getString("favorites", "[]") ?: "[]")
        set(value) = prefs.edit().putString("favorites", JSONArray(value.map(::favoriteJson)).toString()).apply()

    var recentPlaces: List<Place>
        get() = try {
            val a = JSONArray(prefs.getString("recentPlaces", "[]"))
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Place(o.getString("name"), o.optString("detail"), o.getDouble("lat"), o.getDouble("lon"))
            }
        } catch (e: Exception) {
            emptyList()
        }
        set(value) = prefs.edit().putString("recentPlaces", JSONArray(value.map {
            JSONObject().put("name", it.name).put("detail", it.detail).put("lat", it.lat).put("lon", it.lon)
        }).toString()).apply()

    private fun favoriteJson(f: Favorite): JSONObject = when (f) {
        is BusFavorite -> JSONObject().put("type", "bus").put("stop", f.stop).put("line", f.line).put("name", f.name)
        is MetroFavorite -> JSONObject().put("type", "metro").put("line", f.line).put("lineName", f.lineName)
            .put("color", f.color).put("station", f.station).put("name", f.name)
    }

    private fun parseFavorites(json: String): List<Favorite> = try {
        val a = JSONArray(json)
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            if (o.getString("type") == "bus") BusFavorite(o.getString("stop"), o.optString("line"), o.getString("name"))
            else MetroFavorite(o.getString("line"), o.getString("lineName"), o.getString("color"), o.getString("station"), o.getString("name"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private fun putSecret(name: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val data = cipher.iv + cipher.doFinal(value.toByteArray())
        prefs.edit().putString(name, Base64.encodeToString(data, Base64.NO_WRAP)).apply()
    }

    private fun secret(name: String): String = try {
        val data = Base64.decode(prefs.getString(name, null) ?: return "", Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data, 0, IV_SIZE))
        String(cipher.doFinal(data, IV_SIZE, data.size - IV_SIZE))
    } catch (e: Exception) {
        ""
    }

    private companion object {
        const val KEY_ALIAS = "settings"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
