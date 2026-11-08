package com.surpride.app.vehicle

enum class DataOrigin { PLEOS_SERVICE, FAKE }
enum class ConnectionStatus { IDLE, INITIALIZED, RESPONDING, FAILED, STOPPED }
enum class ReadingStatus { PENDING, AVAILABLE, NO_DATA, UNSUPPORTED, NOT_QUERIED, PERMISSION_DENIED, ERROR, TIMEOUT }
enum class VehicleMetric {
    BATTERY, FUEL, ODOMETER, RANGE, GEAR, MOTION;
    val isPublicSdkRead: Boolean get() = this == BATTERY || this == FUEL || this == ODOMETER
}

data class VehicleReading(
    val status: ReadingStatus = ReadingStatus.PENDING,
    val value: Double? = null,
    val capability: String = "확인 중",
    val detail: String = "",
    val receivedAtMillis: Long? = null
)

data class VehicleState(
    val origin: DataOrigin = DataOrigin.PLEOS_SERVICE,
    val connection: ConnectionStatus = ConnectionStatus.IDLE,
    val readings: Map<VehicleMetric, VehicleReading> = VehicleMetric.entries.associateWith {
        if (it.isPublicSdkRead) VehicleReading() else VehicleReading(
            status = ReadingStatus.NOT_QUERIED,
            capability = "공개 SDK 조회 계약 미확인",
            detail = if (it == VehicleMetric.MOTION) "기어·속도 미조회: 정차/주행 판정 안 함" else "비공개·문서 제외 API는 호출하지 않음"
        )
    },
    val subscriptions: Int = 0,
    val receivedUpdates: Int = 0,
    val lastResponseMillis: Long? = null,
    val cleanupErrors: Int = 0
)

data class DataFailure(val status: ReadingStatus, val detail: String)
data class CapabilityResult(val readable: Boolean, val access: String = "", val failure: DataFailure? = null)
data class ReadResult(val value: Double?, val failure: DataFailure? = null)

/**
 * 2.3.2 publishes obfuscated exception classes; do not reflect on private SDK fields.
 * Only exact documented ErrorCode messages (observed on this AVD) are recognized.
 * Unknown messages remain ERROR and are not printed, since they may contain private data.
 */
internal fun vehicleFailure(e: Exception): DataFailure {
    val code = e.message
    val status = when {
        e is SecurityException || code == "ERROR_SECURITY_PERMISSION" -> ReadingStatus.PERMISSION_DENIED
        code == "ERROR_NOT_SUPPORTED_PROPERTY" || code == "ERROR_NOT_SUPPORTED_AREA" -> ReadingStatus.UNSUPPORTED
        code == "ERROR_EMPTY_RESULT" -> ReadingStatus.NO_DATA
        else -> ReadingStatus.ERROR
    }
    val documented = setOf("ERROR_NOT_SUPPORTED_PROPERTY", "ERROR_NOT_SUPPORTED_AREA", "ERROR_NOT_SUPPORTED_VALUE_TYPE",
        "ERROR_SECURITY_PERMISSION", "ERROR_SERVICE_CONNECTION_FAILURE", "ERROR_INTERNAL_SERVICE", "ERROR_DRIVING_RESTRICTED",
        "ERROR_EMPTY_RESULT", "ERROR_UNDEFINED")
    return DataFailure(status, if (code in documented) code!! else "SDK 오류: ${e.javaClass.simpleName}")
}
