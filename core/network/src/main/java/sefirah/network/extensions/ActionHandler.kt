package sefirah.network.extensions

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sefirah.domain.model.ActionInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActionHandler @Inject constructor() {
    private val _actionsByDevice = MutableStateFlow<Map<String, List<ActionInfo>>>(emptyMap())
    val actionsByDevice: StateFlow<Map<String, List<ActionInfo>>> = _actionsByDevice.asStateFlow()

    private val mutex = Mutex()

    suspend fun addAction(deviceId: String, action: ActionInfo) {
        mutex.withLock {
            val currentMap = _actionsByDevice.value.toMutableMap()
            val deviceActions = currentMap.getOrDefault(deviceId, emptyList()).toMutableList()
            
            if (deviceActions.none { it.actionId == action.actionId }) {
                deviceActions.add(action)
                currentMap[deviceId] = deviceActions
                _actionsByDevice.value = currentMap
            }
        }
    }

    suspend fun clearDeviceActions(deviceId: String) {
        mutex.withLock {
            val currentMap = _actionsByDevice.value.toMutableMap()
            currentMap.remove(deviceId)
            _actionsByDevice.value = currentMap
        }
    }
} 