package engine.repository

import engine.quiz.Quiz
import org.springframework.data.jpa.repository.JpaRepository

interface QuizRepository : JpaRepository<Quiz, Int>
