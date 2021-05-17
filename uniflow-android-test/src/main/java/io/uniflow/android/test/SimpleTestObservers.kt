package io.uniflow.android.test

import androidx.lifecycle.Observer
import io.uniflow.android.AndroidDataFlow
import io.uniflow.android.livedata.LiveDataPublisher
import io.uniflow.core.flow.data.UIData
import io.uniflow.core.flow.data.UIEvent
import io.uniflow.core.flow.data.UIState
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.assertEquals

class SimpleDataObserver<T>(val callback: (T) -> Unit) : Observer<T> {
    val values = arrayListOf<T>()

    override fun onChanged(t: T) {
        values.add(t)
        callback(t)
    }
}

class TestViewObserver {
    val values = ConcurrentLinkedQueue<UIData>()
    val states = SimpleDataObserver<UIState> { values.add(it) }
    val events = SimpleDataObserver<UIEvent> { values.add(it) }

    val lastStateOrNull: UIState?
        get() = states.values.lastOrNull()
    val statesCount
        get() = states.values.count()
    val eventsCount
        get() = events.values.count()
    val lastEventOrNull
        get() = events.values.lastOrNull()
    val lastValueOrNull
        get() = values.lastOrNull()

    @Deprecated("better use verifySequence")
    fun assertReceived(vararg any: UIData) = verifySequence(*any)
    fun verifySequence(vararg testingData: UIData) {
        val testingValues = testingData.toList()
        assertEquals(values.size, testingValues.size, "Incorrect size of list ")
        values.forEachIndexed { index, uiData ->
            assertEquals(uiData, testingValues[index],
                    "Wrong values at [$index] - expecting: ${uiData::class.simpleName}"
            )
        }
    }
}

fun AndroidDataFlow.createTestObserver(): TestViewObserver {
    val tester = TestViewObserver()
    val liveDataPublisher = defaultDataPublisher as LiveDataPublisher
    liveDataPublisher.states.observeForever(tester.states)
    liveDataPublisher.events.observeForever { tester.events.onChanged(it.content) }
    return tester
}

fun LiveDataPublisher.createTestObserver(): TestViewObserver {
    val tester = TestViewObserver()
    states.observeForever(tester.states)
    events.observeForever { tester.events.onChanged(it.content) }
    return tester
}