package com.mapbox.navigation.utils.internal

class RequestMap<T> {
    private val requestIdGenerator = RequestIdGenerator()
    private val requests = mutableMapOf<Int, T>()

    fun put(value: T): Int {
        val requestId = requestIdGenerator.generateRequestId()
        put(requestId, value)
        return requestId
    }

    fun put(requestId: Int, value: T) {
        requests.put(requestId, value)?.let {
            throw IllegalArgumentException(
                "The request with ID '$requestId' is already in progress."
            )
        }
    }

    fun get(id: Int): T? = requests[id]

    fun remove(id: Int): T? = requests.remove(id)

    fun removeAll(): Collection<T> {
        val values = requests.values
        requests.clear()
        return values
    }

    fun generateNextRequestId() = requestIdGenerator.generateRequestId()
}

private class RequestIdGenerator {
    private var lastRequestId = 0
    fun generateRequestId() = ++lastRequestId
}
