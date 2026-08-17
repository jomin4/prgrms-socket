package com.back.global.sse

import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Component
class SseEmitters {
    private val emittersByKey = ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>>()

    fun connect(key: String, timeout: Long = 60 * 60 * 1000L): SseEmitter {
        val emitter = SseEmitter(timeout)
        val emitters = emittersByKey.computeIfAbsent(key) { CopyOnWriteArrayList() }
        emitters.add(emitter)

        emitter.onCompletion { emitters.remove(emitter) }
        emitter.onTimeout { emitters.remove(emitter) }
        emitter.onError { emitters.remove(emitter) }

        return emitter
    }

    fun send(key: String, eventName: String, data: Any) {
        val emitters = emittersByKey[key] ?: return

        emitters.forEach { emitter ->
            try {
                emitter.send(
                    SseEmitter.event()
                        .name(eventName)
                        .data(data)
                )
            } catch (_: Exception) {
                emitters.remove(emitter)
            }
        }
    }
}
