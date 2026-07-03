package tech.sergiodelgado.saastemplate.auth

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNull

/** Concrete type so mockk can infer S (avoids wildcard-capture type inference issues on uri()). */
private interface TestHeadersSpec : RestClient.RequestHeadersUriSpec<TestHeadersSpec>

class MicrosoftGraphAvatarClientTest {

    private val restClient = mockk<RestClient>(relaxed = true)
    private val client = MicrosoftGraphAvatarClient(restClient)

    private val photoBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) // JPEG magic bytes

    private fun stubPhoto(responseEntity: ResponseEntity<ByteArray>) {
        val getSpec = mockk<TestHeadersSpec>(relaxed = true)
        val responseSpec = mockk<RestClient.ResponseSpec> {
            every { toEntity(ByteArray::class.java) } returns responseEntity
        }
        every { restClient.get() } returns getSpec
        every { getSpec.uri(any<String>()) } returns getSpec
        every { getSpec.header(any(), any()) } returns getSpec
        every { getSpec.retrieve() } returns responseSpec
    }

    private fun stubPhotoThrows(exception: Exception) {
        val getSpec = mockk<TestHeadersSpec>(relaxed = true)
        val responseSpec = mockk<RestClient.ResponseSpec> {
            every { toEntity(ByteArray::class.java) } throws exception
        }
        every { restClient.get() } returns getSpec
        every { getSpec.uri(any<String>()) } returns getSpec
        every { getSpec.header(any(), any()) } returns getSpec
        every { getSpec.retrieve() } returns responseSpec
    }

    @Test
    fun `returns AvatarBytes with content-type and bytes on success`() {
        val headers = HttpHeaders().also { it.contentType = MediaType.IMAGE_JPEG }
        stubPhoto(ResponseEntity(photoBytes, headers, HttpStatus.OK))

        val result = client.fetchPhoto("token-abc")

        expectThat(result?.bytes).isEqualTo(photoBytes)
        expectThat(result?.contentType).isEqualTo("image/jpeg")
    }

    @Test
    fun `falls back to image-jpeg when response has no content-type`() {
        stubPhoto(ResponseEntity(photoBytes, HttpHeaders.EMPTY, HttpStatus.OK))

        val result = client.fetchPhoto("token-abc")

        expectThat(result?.contentType).isEqualTo("image/jpeg")
    }

    @Test
    fun `sends Authorization header with Bearer token`() {
        val getSpec = mockk<TestHeadersSpec>(relaxed = true)
        val responseSpec = mockk<RestClient.ResponseSpec> {
            every { toEntity(ByteArray::class.java) } returns ResponseEntity(photoBytes, HttpStatus.OK)
        }
        every { restClient.get() } returns getSpec
        every { getSpec.uri(any<String>()) } returns getSpec
        every { getSpec.header(any(), any()) } returns getSpec
        every { getSpec.retrieve() } returns responseSpec

        client.fetchPhoto("my-token")

        verify { getSpec.header("Authorization", "Bearer my-token") }
    }

    @Test
    fun `returns null on Graph 404 (no photo set)`() {
        stubPhotoThrows(
            HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, ByteArray(0), null)
        )

        val result = client.fetchPhoto("token-abc")

        expectThat(result).isNull()
    }

    @Test
    fun `returns null on connection failure`() {
        stubPhotoThrows(RuntimeException("Connection refused"))

        val result = client.fetchPhoto("token-abc")

        expectThat(result).isNull()
    }

    @Test
    fun `returns null when response body is empty`() {
        stubPhoto(ResponseEntity(ByteArray(0), HttpStatus.OK))

        val result = client.fetchPhoto("token-abc")

        expectThat(result).isNull()
    }

    @Test
    fun `returns null when response body is null`() {
        stubPhoto(ResponseEntity(null, HttpStatus.OK))

        val result = client.fetchPhoto("token-abc")

        expectThat(result).isNull()
    }
}
