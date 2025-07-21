package sefirah.network.extensions

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import sefirah.domain.model.ActionMessage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActionHandler @Inject constructor() {

    private val _actions = MutableStateFlow<List<ActionMessage>>(emptyList())
    val actions = _actions.asStateFlow()

    fun addAction(action: ActionMessage) {
        val currentActions = _actions.value.toMutableList()
        if (currentActions.none { it.actionId == action.actionId }) {
            _actions.value = currentActions + action
        }
    }

    fun clearActions() {
        _actions.value = emptyList()
    }
} 