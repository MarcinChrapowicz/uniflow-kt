package io.uniflow.core.flow

import io.uniflow.core.logger.UniFlowLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive

/**
 * Unidirectional Data Flow
 *
 *
 */
interface DataFlow {

    /**
     * Current used coroutine scope
     */
    val coroutineScope: CoroutineScope

    /**
     * Send an event
     * @param event
     */
    suspend fun sendEvent(event: UIEvent): UIState?

    /**
     * Return current State if any
     * @return state
     */
    fun getCurrentState(): UIState?

    /**
     * Apply new state to current state
     * @param state
     */
    suspend fun applyState(state: UIState)

    /**
     * If any flowError occurs and is not caught, this function catch it
     * @param error
     */
    suspend fun onError(error: Exception) {
        UniFlowLogger.logError("Got Flow Error", error)
        throw error
    }

    /**
     * Make an StateAction to update the current state
     *
     * @param onStateUpdate - function to produce a new state, from the current state
     */
    fun setState(onStateUpdate: StateUpdateFunction, onActionError: ErrorFunction): StateAction {
        return StateAction(onStateUpdate, onActionError).let {
            onAction(it)
            it
        }
    }

    fun setState(onStateUpdate: StateUpdateFunction): StateAction {
        return StateAction(onStateUpdate).let {
            onAction(it)
            it
        }
    }

    /**
     * Make an StateAction that can update or not the current state
     * More for side effects
     *
     * @param onStateAction - function run against the current state
     */
    fun withState(onStateAction: StateActionFunction, onActionError: ErrorFunction): StateAction {
        return StateAction(onStateAction, onActionError).let {
            onAction(it)
            it
        }
    }

    fun withState(onStateAction: StateActionFunction): StateAction {
        return StateAction(onStateAction).let {
            onAction(it)
            it
        }
    }

    /**
     * An action that can trigger several state changes
     *
     * stateFlowFunction allow to use the StateFlowAction.setState(...) function to set any new state
     *
     * @param stateFlowFunction - flow state
     * @param errorFunction - flowError function
     */
    fun stateFlow(onStateFlow: StateFlowFunction, onActionError: ErrorFunction): StateFlowAction {
        return StateFlowAction(this, onStateFlow, onActionError).let {
            onStateFlow(it)
            it
        }
    }

    fun stateFlow(onStateFlow: StateFlowFunction): StateFlowAction {
        return StateFlowAction(this, onStateFlow).let {
            onStateFlow(it)
            it
        }
    }

    fun onStateFlow(stateFlowAction: StateFlowAction) {
        coroutineScope.apply {
            if (isActive) {
                launchOnIO {
                    proceedStateFlow(stateFlowAction)
                }
            }
        }
    }

    suspend fun proceedStateFlow(stateFlowAction: StateFlowAction) {
        try {
            stateFlowAction.onStateFlow(stateFlowAction, getCurrentState())
        } catch (e: Exception) {
            if (stateFlowAction.errorFunction != null) {
                onActionError(StateAction(errorFunction = stateFlowAction.errorFunction), e)
            } else {
                onError(e)
            }
        }
    }

    /**
     * Execute the action & catch any flowError
     * @param action
     */
    fun onAction(action: StateAction) {
        coroutineScope.apply {
            if (isActive) {
                UniFlowLogger.log("DataFlow.onAction run $action")
                launchOnIO {
                    proceedAction(action)
                }
            } else {
                UniFlowLogger.log("DataFlow.onAction action cancelled")
            }
        }
    }

    /**
     * Execute action on coroutine
     */
    suspend fun proceedAction(action: StateAction) {
        try {
            val result = action.stateFunction?.invoke(action, getCurrentState())
            if (result is UIState) {
                applyState(result)
            }
        } catch (e: Exception) {
            onActionError(action, e)
        }
    }

    /**
     * Handle flowError catch for given withState
     * @param action
     * @param error
     */
    fun onActionError(action: StateAction, error: Exception) {
        coroutineScope.apply {
            if (isActive) {
                UniFlowLogger.log("DataFlow.onActionError run $action for $error")
                launchOnIO {
                    if (action.errorFunction != null) {
                        val failState = action.errorFunction.let {
                            it.invoke(action, error)
                        }
                        failState?.let { applyState(failState) }
                    } else onError(error)
                }
            } else {
                UniFlowLogger.log("DataFlow.onActionError cancelled for $error")
            }
        }
    }
}