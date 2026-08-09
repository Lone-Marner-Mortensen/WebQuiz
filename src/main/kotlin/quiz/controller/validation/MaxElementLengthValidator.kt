package quiz.controller.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext

class MaxElementLengthValidator : ConstraintValidator<MaxElementLength, List<String>?> {

    private var max: Int = 0

    override fun initialize(constraintAnnotation: MaxElementLength) {
        max = constraintAnnotation.max
    }

    override fun isValid(value: List<String>?, context: ConstraintValidatorContext): Boolean {
        return value?.all { it.length <= max } ?: true
    }
}
