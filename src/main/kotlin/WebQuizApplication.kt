package quiz

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class WebQuizApplication

fun main(args: Array<String>) {
    runApplication<WebQuizApplication>(*args)
}
