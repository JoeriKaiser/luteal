package fr.luteal.core.common

sealed interface LutealResult<out T> {
    data class Success<out T>(val data: T) : LutealResult<T>
    data class Error(val exception: Throwable, val message: String? = null) : LutealResult<Nothing>
    data object Loading : LutealResult<Nothing>
}

inline fun <T, R> LutealResult<T>.map(transform: (T) -> R): LutealResult<R> {
    return when (this) {
        is LutealResult.Success -> LutealResult.Success(transform(data))
        is LutealResult.Error -> this
        is LutealResult.Loading -> LutealResult.Loading
    }
}

fun <T> LutealResult<T>.getOrNull(): T? {
    return (this as? LutealResult.Success)?.data
}

fun <T> LutealResult<T>.getOrDefault(defaultValue: T): T {
    return (this as? LutealResult.Success)?.data ?: defaultValue
}
