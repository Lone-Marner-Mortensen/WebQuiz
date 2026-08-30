package quiz.domain.model

data class Question(
    val text: String,
    val options: List<String>,
    val answer: Int
) {
    init {
        require(text.isNotBlank() && text.length <= 100) {
            "Question text must contain between 1 and 100 characters"
        }
        require(options.size in 2..10) { "Question must have between 2 and 10 options" }
        require(options.all { it.isNotBlank() && it.length <= 50 }) {
            "Question options must contain between 1 and 50 characters"
        }
        require(answer in options.indices) { "Answer index is out of range" }
    }
}
