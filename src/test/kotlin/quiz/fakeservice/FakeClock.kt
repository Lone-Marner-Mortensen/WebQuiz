package quiz.fakeservice

import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import quiz.domain.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
@Primary
class FakeClock(
    var now: OffsetDateTime = OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
): Clock {
    override fun now(): OffsetDateTime = now
}
