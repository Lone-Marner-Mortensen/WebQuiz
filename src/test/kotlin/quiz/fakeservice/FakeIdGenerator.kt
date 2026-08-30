package quiz.fakeservice

import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import quiz.domain.IdGenerator

@Service
@Primary
class FakeIdGenerator(
    val id: String = "<random_uuid>",
): IdGenerator {
    override fun createId(): String = id
}
