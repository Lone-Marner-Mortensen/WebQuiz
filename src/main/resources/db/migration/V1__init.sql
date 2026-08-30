CREATE TABLE users (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL
);

CREATE TABLE quizzes (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    title VARCHAR(75) NOT NULL,
    author_id VARCHAR(255) NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE questions (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    text VARCHAR(100) NOT NULL,
    answer INTEGER NOT NULL,
    quiz_id VARCHAR(255) NOT NULL REFERENCES quizzes(id) ON DELETE CASCADE,
    question_order INTEGER NOT NULL,
    CONSTRAINT questions_answer_non_negative CHECK (answer >= 0),
    CONSTRAINT questions_quiz_order_unique UNIQUE (quiz_id, question_order)
);

CREATE TABLE question_options (
    question_id VARCHAR(255) NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    option_value VARCHAR(50) NOT NULL,
    option_order INTEGER NOT NULL,
    CONSTRAINT question_options_order_unique UNIQUE (question_id, option_order)
);

CREATE TABLE quiz_completions (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    quiz_id VARCHAR(255) NOT NULL REFERENCES quizzes(id) ON DELETE CASCADE,
    user_id VARCHAR(255) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT quiz_completions_user_quiz_unique UNIQUE (user_id, quiz_id)
);

CREATE INDEX quizzes_created_at_idx ON quizzes (created_at DESC);
CREATE INDEX questions_quiz_id_idx ON questions (quiz_id);
CREATE INDEX question_options_question_id_idx ON question_options (question_id);
CREATE INDEX quiz_completions_quiz_id_idx ON quiz_completions (quiz_id);
CREATE INDEX quiz_completions_user_completed_idx ON quiz_completions (user_id, completed_at DESC);
