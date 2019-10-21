/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed launchOn an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.uniflow.core.flow

import io.uniflow.core.logger.UniFlowLogger
import io.uniflow.core.threading.launchOnIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.isActive

/**
 * Unidirectional Data Flow
 *
 * @author Arnaud Giuliani
 */
interface DataFlow {

    /**
     * Current used coroutine scope
     */
    val coroutineScope: CoroutineScope

    /**
     * Actor to buffer incoming actions
     */
    val actorFlow: SendChannel<StateAction>

    /**
     * Send an event
     * @param event
     */
    suspend fun sendEvent(event: UIEvent): UIState?

    /**
     * Return current State if any
     * @return state
     */
    val state: UIState?

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
        UniFlowLogger.logError("Got error '${error.message}' on state '$state'", error)
        throw error
    }

    /**
     * Make an StateAction to update the current state
     *
     * @param onStateUpdate - function to produce a new state, from the current state
     */
    fun setState(onStateUpdate: StateUpdateFunction, onActionError: ErrorFunction) {
        onAction(StateAction(onStateUpdate, onActionError))
    }

    fun setState(onStateUpdate: StateUpdateFunction) {
        onAction(StateAction(onStateUpdate))
    }

    /**
     * Make an StateAction that can update or not the current state
     * More for side effects
     *
     * @param onStateAction - function run against the current state
     */
    fun withState(onStateAction: StateActionFunction, onActionError: ErrorFunction) {
        onAction(StateAction(onStateAction, onActionError))
    }

    fun withState(onStateAction: StateActionFunction) {
        onAction(StateAction(onStateAction))
    }

    /**
     * An action that can trigger several state changes
     *
     * stateFlowFunction allow to use the StateFlowAction.setState(...) function to set any new state
     *
     * @param stateFlowFunction - flow state
     * @param errorFunction - flowError function
     */
    fun stateFlow(onStateFlow: StateFlowFunction, onActionError: ErrorFunction) {
        onStateFlow(StateFlowAction(this, onStateFlow, onActionError))
    }

    fun stateFlow(onStateFlow: StateFlowFunction) {
        onStateFlow(StateFlowAction(this, onStateFlow))
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
            stateFlowAction.onStateFlow(stateFlowAction, state)
        } catch (e: Exception) {
            if (stateFlowAction.errorFunction != null) {
                onActionError(StateAction(errorFunction = stateFlowAction.errorFunction), e)
            } else {
                onError(e)
            }
        }
    }

//    fun onAction(action: StateAction) {
//        coroutineScope.apply {
//            if (isActive) {
//                UniFlowLogger.log("DataFlow.onAction run $action")
//                launchOnIO {
//                    proceedAction(action)
//                }
//            } else {
//                UniFlowLogger.log("DataFlow.onAction action cancelled")
//            }
//        }
//    }
    /**
     * Execute the action & catch any flowError
     * @param action
     */
    fun onAction(action: StateAction) {
        coroutineScope.apply {
            if (isActive) {
                UniFlowLogger.log("DataFlow onAction $action")
                actorFlow.offer(action)
            } else {
                UniFlowLogger.log("DataFlow onAction $action cancelled")
            }
        }
    }

    /**
     * Execute action on coroutine
     */
    suspend fun proceedAction(action: StateAction) {
        try {
            val result = action.stateFunction?.invoke(action, state)
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
                UniFlowLogger.log("DataFlow onActionError '${error.message}' for action $action ")
                launchOnIO {
                    if (action.errorFunction != null) {
                        val failState = action.errorFunction.let {
                            it.invoke(action, error)
                        }
                        failState?.let { applyState(failState) }
                    } else onError(error)
                }
            } else {
                UniFlowLogger.log("DataFlow onActionError cancelled for action $action")
            }
        }
    }
}