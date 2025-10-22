// scheduler/presentation/schedule/InteractiveSchedulingState.kt
package stevedaydream.scheduler.presentation.schedule

enum class ScheduleStep(val description: String) {
    INITIALIZING("正在初始化班表..."),
    FILLING_N("正在填入夜班 (N)..."),
    REVIEWING_N("檢視/編輯 夜班 (N)"),
    FILLING_D("正在填入日班 (D)..."),
    REVIEWING_D("檢視/編輯 日班 (D)"),
    FILLING_S("正在填入白班 (S)..."),
    REVIEWING_S("檢視/編輯 白班 (S)"),
    FILLING_OFF("正在填入休假 (OFF)..."),
    REVIEWING_OFF("檢視/編輯 休假 (OFF)"),
    FINALIZING("正在儲存最終班表..."),
    COMPLETE("排班完成"),
    ERROR("發生錯誤")
}