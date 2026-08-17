package com.back.domain.sse.controller

import com.back.global.sse.SseEmitters
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/sse")
@CrossOrigin(origins = ["https://cdpn.io"])
class SseController(
    private val sseEmitters: SseEmitters
) {
    @GetMapping("/connect/{key}", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun connect(@PathVariable key: String): SseEmitter {
        return sseEmitters.connect(key)
    }
}
