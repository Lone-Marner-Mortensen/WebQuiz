package quiz.repository.mapper

import org.springframework.data.domain.Page
import quiz.domain.response.PagedResult

fun <T : Any> Page<T>.toPagedResult(): PagedResult<T> {
    return PagedResult(
        content = content,
        pageNumber = number,
        pageSize = size,
        totalElements = totalElements,
        totalPages = totalPages
    )
}
