package quiz.domain

import org.springframework.stereotype.Service
import java.util.UUID

interface IdGenerator {
    fun createId(): String
}

@Service
class IdGeneratorImpl : IdGenerator {
    override fun createId(): String = UUID.randomUUID().toString()
}
