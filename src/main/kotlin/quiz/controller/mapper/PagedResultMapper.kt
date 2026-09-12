package quiz.controller.mapper

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import quiz.domain.model.PagedResult

fun <T : Any> PagedResult<T>.toPage(): Page<T> {
    return PageImpl(content, PageRequest.of(pageNumber, pageSize), totalElements)
}
