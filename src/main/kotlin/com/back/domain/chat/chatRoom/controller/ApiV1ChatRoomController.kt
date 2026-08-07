package com.back.domain.chat.chatRoom.controller

import com.back.domain.chat.chatRoom.entity.ChatRoom
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/v1/chat/rooms")
@CrossOrigin(
    originPatterns = ["https://cdpn.io"],
)
class ApiV1ChatRoomController {
    val chatRooms: MutableList<ChatRoom> = mutableListOf(
        ChatRoom(
            1,
            LocalDateTime.now(),
            LocalDateTime.now(),
            "풋살하실 분?"
        ),
        ChatRoom(
            2,
            LocalDateTime.now(),
            LocalDateTime.now(),
            "농구 하실 분?"
        ),
        ChatRoom(
            3,
            LocalDateTime.now(),
            LocalDateTime.now(),
            "야구 하실 분?"
        )
    )

    @GetMapping
    fun getItems() = chatRooms.toList()

    @GetMapping("/{id}")
    fun getItem(@PathVariable id: Int): ChatRoom? =
        findById(id)

    data class ChatCreateRoomReqBody(
        val name: String
    )

    @PostMapping
    fun createItem(
        @RequestBody reqBody: ChatCreateRoomReqBody
    ): ChatRoom {
        val newId = (chatRooms.maxOfOrNull { it.id } ?: 0) + 1

        val newChatRoom = ChatRoom(
            newId,
            LocalDateTime.now(),
            LocalDateTime.now(),
            reqBody.name
        )

        chatRooms.add(newChatRoom)

        return newChatRoom
    }

    private fun findById(id: Int): ChatRoom? =
        chatRooms.find { it.id == id }
}