package com.back.domain.chat.chatMessage.controller

import com.back.domain.chat.chatMessage.entity.ChatMessage
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
import com.back.global.sse.SseEmitters

@RestController
@RequestMapping("/api/v1/chat/rooms/{chatRoomId}/messages")
@CrossOrigin(origins = ["https://cdpn.io"])
class ApiV1ChatMessageController (
    private val sseEmitters: SseEmitters
) {
    private var lastChatMessageId = 0

    private val chatMessagesByRoomId: MutableMap<Int, MutableList<ChatMessage>> = mutableMapOf(
        1 to mutableListOf(
            ChatMessage(
                ++lastChatMessageId,
                LocalDateTime.now(),
                LocalDateTime.now(),
                1,
                "김철수",
                "풋살하실 분 계신가요?"
            ),
            ChatMessage(
                ++lastChatMessageId,
                LocalDateTime.now(),
                LocalDateTime.now(),
                1,
                "이영희",
                "네, 저요!"
            )
        ),
        2 to mutableListOf(
            ChatMessage(
                ++lastChatMessageId,
                LocalDateTime.now(),
                LocalDateTime.now(),
                2,
                "박철수",
                "농구하실 분 계신가요?"
            ),
            ChatMessage(
                ++lastChatMessageId,
                LocalDateTime.now(),
                LocalDateTime.now(),
                2,
                "김영희",
                "네, 저요!"
            )
        ),
        3 to mutableListOf(
            ChatMessage(
                ++lastChatMessageId,
                LocalDateTime.now(),
                LocalDateTime.now(),
                3,
                "이철수",
                "야구하실 분 계신가요?"
            ),
            ChatMessage(
                ++lastChatMessageId,
                LocalDateTime.now(),
                LocalDateTime.now(),
                3,
                "박영희",
                "네, 저요!"
            )
        )
    )


    @GetMapping
    fun getItems(
        @PathVariable chatRoomId: Int,
        @RequestParam(defaultValue = "-1") afterChatMessageId: Int,
    ): List<ChatMessage> =
        chatMessagesByRoomId[chatRoomId]
            ?.filter { it.id > afterChatMessageId }
            ?.toList()
            ?: emptyList()


    data class ChatMessageWriteReqBody(
        val writerName: String,
        val content: String
    )

    @PostMapping
    fun write(
        @PathVariable chatRoomId: Int,
        @RequestBody reqBody: ChatMessageWriteReqBody
    ): ChatMessage {
        val chatMessages = chatMessagesByRoomId.getOrPut(chatRoomId) { mutableListOf() }

        val chatMessage = ChatMessage(
            ++lastChatMessageId,
            LocalDateTime.now(),
            LocalDateTime.now(),
            chatRoomId,
            reqBody.writerName,
            reqBody.content
        )

        chatMessages.add(chatMessage)

        sseEmitters.send("chat__room__$chatRoomId", "chat__messageCreated", chatMessage)

        return chatMessage
    }


    @PostMapping("/entry")
    fun entry(@PathVariable chatRoomId: Int) {
        val systemMessage = ChatMessage(
            ++lastChatMessageId,
            LocalDateTime.now(),
            LocalDateTime.now(),
            chatRoomId,
            "system",
            "새로운 사용자가 입장했습니다."
        )

        sseEmitters.send("chat__room__$chatRoomId", "chat__systemMessageCreated", systemMessage)
    }

    @PostMapping("/exit")
    fun exit(@PathVariable chatRoomId: Int) {
        val systemMessage = ChatMessage(
            ++lastChatMessageId,
            LocalDateTime.now(),
            LocalDateTime.now(),
            chatRoomId,
            "system",
            "어떤 사용자가 퇴장했습니다."
        )

        sseEmitters.send("chat__room__$chatRoomId", "chat__systemMessageCreated", systemMessage)
    }
}
