
# Uniflow 🦄- Simple Unidirectionnel Data Flow for Android & Kotlin

## DataFlow: emit flow of states & events

We call a `Dataflow` a component that will emits states and events. This component is used in a Unidirectional data flow architecture approach 
to ensure:
- Single source of truth: data only come from `Dataflow`
- States/Events are immutable, to avoid any side effects
- A `Dataflow` is using an `action` to help emit a new state or an event

A `DataFlow` component is backed by a coroutines [`Actor`](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.channels/actor.html) to buffer and dispatch actions in order.

Uniflow provides the `DataFlow` interface that is implementated by `AndroidDataFlow` for Android platform.

## States as immutable data

To describe your data flow states, extends the `UIState` class directly or use it as a sealed class as follow:

```kotlin
class WeatherStates : UIState(){
	object LoadingWeather : WeatherStates()
	data class WeatherState(val day : String, val temperature : String) : WeatherStates()
}
```

## Updating our state with an Action

Your ViewModel class, aka your DataFlow, will provide `actions` that will trigger states and events.

An action is a simple function, like that:

```kotlin
class WeatherDataFlow(...) : AndroidDataFlow() {
    
    // getWeather action
    fun getWeather() = action {
        ...
    }
}
```

From our `action { }` codeblock, we can set a new state with `setState` function:

```kotlin
// update the current state
fun getWeather() = action {
    // return directly your state object
    setState { WeatherState(...) }
}
```

## Observing DataFlow states

To listen incoming states from your Activity/Fragment, we need to use `onStates` function:

```kotlin
class MyActivity : AppCompatActivity(){
    
    fun onCreate(...){

        // Observe incoming states
        onStates(weatherFlow) { state ->
            when (state) {
                // react on WeatherState update
                is WeatherState -> showWeather(state)
            }
        }
    }
}
```

## Getting the current state

From our `ViewModel` we can have access to the current state with the first action parameter: `currentState`

```kotlin
class WeatherDataFlow(...) : AndroidDataFlow() {

    fun getWeather() = action { currentState ->

    }
}
```

You can also use `onState<T> { t -> ... }` to safely execute a code for given state:

```kotlin
class WeatherDataFlow(...) : AndroidDataFlow() {

    fun getWeather() = action { 
        onState<MyState> { myState ->
            // ...
        }
    }
}
```

## Default state

You can set a "default state" for your Data Flow. This set your first state value, it's not emitted at start.

```kotlin
class WeatherDataFlow() : AndroidDataFlow(defaultState = UIState.Empty) {

    // no state emitted at start
}
```

To emit a state at start, we need to write an init block like following:

```kotlin
class WeatherDataFlow() : AndroidDataFlow() {

    init {
        // Emit first state at start
        action { setState(UIState.Empty) }
    }
    
}
```

----

## [Back To Documentation Topics](../README.md#getting-started--documentation-)
