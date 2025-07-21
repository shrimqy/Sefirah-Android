package sefirah.domain.model

sealed class ConnectionState {
    data object Connected : ConnectionState()
    data class Connecting(val device: String? = null) : ConnectionState()
    data class Disconnected(val forcedDisconnect: Boolean = false) : ConnectionState()
    data class Error(val message: String) : ConnectionState()

    val isDisconnected: Boolean
        get() = this is Disconnected || this is Error
    val isConnected: Boolean
        get() = this is Connected
    val isConnecting : Boolean
        get() = this is Connecting
    val isError: Boolean
        get() = this is Error

    val isForcedDisconnect: Boolean
        get() = this is Disconnected && forcedDisconnect

    val isConnectedOrConnecting : Boolean
        get() = isConnected || isConnecting
}