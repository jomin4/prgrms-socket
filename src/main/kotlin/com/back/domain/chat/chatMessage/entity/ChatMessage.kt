package com.back.domain.chat.chatMessage.entity

import java.time.LocalDateTime

data class ChatMessage(
    val id: Int,
    val createDate: LocalDateTime,
    var modifyDate: LocalDateTime,
    val chatRoomId: Int,
    val writerName: String,
    val content: String
)
