package tech.sergiodelgado.saastemplate.auth

fun interface OrgSuggestions {
    fun getOrgNames(userId: String): List<String>?
}
