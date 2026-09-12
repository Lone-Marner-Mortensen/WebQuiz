package quiz.domain.repository

import org.springframework.stereotype.Service
import java.time.OffsetDateTime

interface Clock {
    fun now(): OffsetDateTime
}

@Service
class ClockImpl : Clock {
    override fun now(): OffsetDateTime = OffsetDateTime.now()
}
