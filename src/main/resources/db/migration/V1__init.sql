CREATE TABLE quizzes (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    author VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE questions (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    text VARCHAR(255) NOT NULL,
    answer INTEGER NOT NULL,
    quiz_id VARCHAR(255) REFERENCES quizzes(id),
    question_order INTEGER
);

CREATE TABLE question_options (
    question_id VARCHAR(255) NOT NULL REFERENCES questions(id),
    option_value VARCHAR(255) NOT NULL
);

CREATE TABLE users (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL
);

CREATE TABLE quiz_completions (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    quiz_id VARCHAR(255) NOT NULL REFERENCES quizzes(id) ON DELETE CASCADE,
    user_email VARCHAR(255) NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
