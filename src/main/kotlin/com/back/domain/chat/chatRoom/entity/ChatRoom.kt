package com.back.domain.chat.chatRoom.entity

import java.time.LocalDateTime


data class ChatRoom(
    val id: Int,
    val createDate: LocalDateTime,
    var modifyDate : LocalDateTime,
    var name : String,
)
