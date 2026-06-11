package tech.sergiodelgado.saastemplate.util

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNull

class EmailNameExtractorTest {

    @Test
    fun `single part local yields capitalized first name and null last name`() {
        val result = extractNameFromEmail("alice@example.com")
        expectThat(result.firstName).isEqualTo("Alice")
        expectThat(result.lastName).isNull()
    }

    @Test
    fun `two dot-separated parts yield first and last name`() {
        val result = extractNameFromEmail("john.doe@example.com")
        expectThat(result.firstName).isEqualTo("John")
        expectThat(result.lastName).isEqualTo("Doe")
    }

    @Test
    fun `three dot-separated parts yield first name and joined last name`() {
        val result = extractNameFromEmail("foo.bar.baz@example.com")
        expectThat(result.firstName).isEqualTo("Foo")
        expectThat(result.lastName).isEqualTo("Bar Baz")
    }

    @Test
    fun `dash separator is handled`() {
        val result = extractNameFromEmail("john-doe@example.com")
        expectThat(result.firstName).isEqualTo("John")
        expectThat(result.lastName).isEqualTo("Doe")
    }

    @Test
    fun `underscore separator is handled`() {
        val result = extractNameFromEmail("john_doe@example.com")
        expectThat(result.firstName).isEqualTo("John")
        expectThat(result.lastName).isEqualTo("Doe")
    }

    @Test
    fun `mixed separators are all split`() {
        val result = extractNameFromEmail("foo.bar-baz_qux@example.com")
        expectThat(result.firstName).isEqualTo("Foo")
        expectThat(result.lastName).isEqualTo("Bar Baz Qux")
    }
}
