package stevedaydream.scheduler.util

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {

    private const val DATE_FORMAT = "yyyy-MM-dd"
    private const val MONTH_FORMAT = "yyyy-MM"
    private const val DISPLAY_DATE_FORMAT = "MM月dd日"
    private const val DISPLAY_MONTH_FORMAT = "yyyy年MM月"

    private val dateFormatter = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
    private val monthFormatter = SimpleDateFormat(MONTH_FORMAT, Locale.getDefault())
    private val displayDateFormatter = SimpleDateFormat(DISPLAY_DATE_FORMAT, Locale("zh", "TW"))
    private val displayMonthFormatter = SimpleDateFormat(DISPLAY_MONTH_FORMAT, Locale("zh", "TW"))

    /**
     * 將時間戳轉換為日期字串 (yyyy-MM-dd)
     */
    fun timestampToDateString(timestamp: Long): String {
        return dateFormatter.format(Date(timestamp))
    }

    /**
     * 將時間戳轉換為月份字串 (yyyy-MM)
     */
    fun timestampToMonthString(timestamp: Long): String {
        return monthFormatter.format(Date(timestamp))
    }

    /**
     * 將日期字串轉換為時間戳
     */
    fun dateStringToTimestamp(dateString: String): Long {
        return try {
            dateFormatter.parse(dateString)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * 取得當前日期字串
     */
    fun getCurrentDateString(): String {
        return timestampToDateString(System.currentTimeMillis())
    }

    /**
     * 取得當前月份字串
     */
    fun getCurrentMonthString(): String {
        return timestampToMonthString(System.currentTimeMillis())
    }

    /**
     * 取得顯示用的日期格式 (MM月dd日)
     */
    fun getDisplayDate(dateString: String): String {
        return try {
            val date = dateFormatter.parse(dateString)
            date?.let { displayDateFormatter.format(it) } ?: dateString
        } catch (e: Exception) {
            dateString
        }
    }

    /**
     * 取得顯示用的月份格式 (yyyy年MM月)
     */
    fun getDisplayMonth(monthString: String): String {
        return try {
            val date = monthFormatter.parse(monthString)
            date?.let { displayMonthFormatter.format(it) } ?: monthString
        } catch (e: Exception) {
            monthString
        }
    }

    /**
     * 取得指定月份的天數
     */
    fun getDaysInMonth(monthString: String): Int {
        return try {
            val date = monthFormatter.parse(monthString) ?: return 30
            val calendar = Calendar.getInstance().apply {
                time = date
            }
            calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        } catch (e: Exception) {
            30
        }
    }

    /**
     * 取得指定月份的所有日期字串列表
     */
    fun getDatesInMonth(monthString: String): List<String> {
        val daysInMonth = getDaysInMonth(monthString)
        return (1..daysInMonth).map { day ->
            "$monthString-${day.toString().padStart(2, '0')}"
        }
    }

    /**
     * 取得星期幾 (0=日, 1=一, ..., 6=六)
     */
    fun getDayOfWeek(dateString: String): Int {
        return try {
            val date = dateFormatter.parse(dateString) ?: return 0
            val calendar = Calendar.getInstance().apply {
                time = date
            }
            calendar.get(Calendar.DAY_OF_WEEK) - 1
        } catch (e: Exception) {
            0
        }
    }

    /**
     * 取得星期幾的中文顯示
     */
    fun getDayOfWeekText(dateString: String): String {
        val dayOfWeek = getDayOfWeek(dateString)
        return when (dayOfWeek) {
            0 -> "日"
            1 -> "一"
            2 -> "二"
            3 -> "三"
            4 -> "四"
            5 -> "五"
            6 -> "六"
            else -> ""
        }
    }

    /**
     * 檢查日期是否為今天
     */
    fun isToday(dateString: String): Boolean {
        return dateString == getCurrentDateString()
    }

    /**
     * 檢查日期是否為週末
     */
    fun isWeekend(dateString: String): Boolean {
        val dayOfWeek = getDayOfWeek(dateString)
        return dayOfWeek == 0 || dayOfWeek == 6
    }

    /**
     * 新增指定天數到日期
     */
    fun addDays(dateString: String, days: Int): String {
        return try {
            val date = dateFormatter.parse(dateString) ?: return dateString
            val calendar = Calendar.getInstance().apply {
                time = date
                add(Calendar.DAY_OF_MONTH, days)
            }
            dateFormatter.format(calendar.time)
        } catch (e: Exception) {
            dateString
        }
    }

    /**
     * 新增指定月數到月份
     */
    fun addMonths(monthString: String, months: Int): String {
        return try {
            val date = monthFormatter.parse(monthString) ?: return monthString
            val calendar = Calendar.getInstance().apply {
                time = date
                add(Calendar.MONTH, months)
            }
            monthFormatter.format(calendar.time)
        } catch (e: Exception) {
            monthString
        }
    }

    /**
     * 計算兩個日期之間的天數差
     */
    fun daysBetween(startDate: String, endDate: String): Int {
        return try {
            val start = dateFormatter.parse(startDate)?.time ?: 0L
            val end = dateFormatter.parse(endDate)?.time ?: 0L
            ((end - start) / (1000 * 60 * 60 * 24)).toInt()
        } catch (e: Exception) {
            0
        }
    }
}