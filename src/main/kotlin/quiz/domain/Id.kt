package quiz.domain

import java.util.UUID

fun createId(): String = UUID.randomUUID().toString()
