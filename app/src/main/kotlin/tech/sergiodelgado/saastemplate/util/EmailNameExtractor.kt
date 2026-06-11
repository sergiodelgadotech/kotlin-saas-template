package tech.sergiodelgado.saastemplate.util

data class ExtractedName(val firstName: String, val lastName: String?)

fun extractNameFromEmail(email: String): ExtractedName {
    val localPart = email.substringBefore("@")
    val parts = localPart.split(".", "-", "_")
    val firstName = parts.first().replaceFirstChar { it.uppercase() }
    val lastName = if (parts.size > 1) {
        parts.drop(1).joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    } else null
    return ExtractedName(firstName, lastName)
}
