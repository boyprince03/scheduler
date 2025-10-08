package stevedaydream.scheduler.util

import android.content.Context
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import com.google.firebase.Timestamp

/**
 * Firestore Timestamp 擴展
 */
fun Timestamp.toMillis(): Long {
    return this.seconds * 1000
}

/**
 * String 顏色轉換為 Compose Color
 */
fun String.toComposeColor(): Color {
    return try {
        Color(android.graphics.Color.parseColor(this))
    } catch (e: Exception) {
        Color.Gray
    }
}

/**
 * Context 擴展 - 顯示 Toast
 */
fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

/**
 * Long 時間戳轉換為人類可讀格式
 */
fun Long.toReadableTime(): String {
    val now = System.currentTimeMillis()
    val diff = now - this

    return when {
        diff < 60_000 -> "剛剛"
        diff < 3600_000 -> "${diff / 60_000} 分鐘前"
        diff < 86400_000 -> "${diff / 3600_000} 小時前"
        diff < 604800_000 -> "${diff / 86400_000} 天前"
        else -> DateUtils.getDisplayDate(DateUtils.timestampToDateString(this))
    }
}

/**
 * Map 擴展 - 安全取得字串值
 */
fun Map<String, Any>.getStringOrNull(key: String): String? {
    return this[key] as? String
}

fun Map<String, Any>.getIntOrNull(key: String): Int? {
    return when (val value = this[key]) {
        is Int -> value
        is Long -> value.toInt()
        is Double -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }
}

fun Map<String, Any>.getBooleanOrNull(key: String): Boolean? {
    return this[key] as? Boolean
}

/**
 * List 擴展 - 安全取得元素
 */
fun <T> List<T>.getOrNull(index: Int): T? {
    return if (index in indices) this[index] else null
}

/**
 * String 擴展 - 驗證
 */
fun String.isValidEmail(): Boolean {
    return android.util.Patterns.EMAIL_ADDRESS.matcher(this).matches()
}

fun String.isValidPhone(): Boolean {
    return android.util.Patterns.PHONE.matcher(this).matches()
}

/**
 * Result 擴展 - 簡化處理
 */
inline fun <T> Result<T>.onSuccessWithValue(action: (T) -> Unit): Result<T> {
    if (isSuccess) {
        action(getOrNull()!!)
    }
    return this
}

inline fun <T> Result<T>.onFailureWithError(action: (Throwable) -> Unit): Result<T> {
    if (isFailure) {
        exceptionOrNull()?.let(action)
    }
    return this
}

/**
 * 時間範圍計算
 */
object TimeRange {
    const val MINUTE_MILLIS = 60_000L
    const val HOUR_MILLIS = 3600_000L
    const val DAY_MILLIS = 86400_000L
    const val WEEK_MILLIS = 604800_000L

    fun minutes(count: Int) = count * MINUTE_MILLIS
    fun hours(count: Int) = count * HOUR_MILLIS
    fun days(count: Int) = count * DAY_MILLIS
    fun weeks(count: Int) = count * WEEK_MILLIS
}