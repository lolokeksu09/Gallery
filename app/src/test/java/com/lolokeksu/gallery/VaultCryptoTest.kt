package com.lolokeksu.gallery

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.Random
import javax.crypto.SecretKey
import org.junit.Test

/**
 * The vault deletes the original after importing, so a defect here loses photos. These tests
 * cover the frame boundaries and every corruption the format claims to detect.
 */
class VaultCryptoTest {
    private val frame = 1 shl 20
    private val random = Random(7)

    private fun bytes(size: Int) = ByteArray(size).also { random.nextBytes(it) }
    private fun sha(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    private fun encrypt(key: SecretKey, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        VaultCrypto.encryptStream(key, ByteArrayInputStream(data), out)
        return out.toByteArray()
    }

    private fun rejects(key: SecretKey, sealed: ByteArray): Boolean = try {
        VaultCrypto.decryptStream(key, ByteArrayInputStream(sealed), null)
        false
    } catch (_: Exception) {
        true
    }

    @Test fun roundTripsAcrossFrameBoundaries() {
        val key = VaultCrypto.newDataKey()
        for (size in listOf(0, 1, 1024, frame - 1, frame, frame + 1, frame * 2 + 12345)) {
            val data = bytes(size)
            val sealed = encrypt(key, data)
            val out = ByteArrayOutputStream()
            val digest = VaultCrypto.decryptStream(key, ByteArrayInputStream(sealed), out)
            assertArrayEquals("plaintext differs at $size bytes", data, out.toByteArray())
            assertArrayEquals("digest differs at $size bytes", sha(data), digest)
        }
    }

    @Test fun encryptionDigestMatchesTheSourceSoImportCanVerify() {
        val key = VaultCrypto.newDataKey()
        val data = bytes(300_000)
        val out = ByteArrayOutputStream()
        val written = VaultCrypto.encryptStream(key, ByteArrayInputStream(data), out)
        assertArrayEquals(sha(data), written)
        assertArrayEquals(written, VaultCrypto.decryptStream(key, ByteArrayInputStream(out.toByteArray()), null))
    }

    @Test fun storedBytesAreNotThePlaintext() {
        val key = VaultCrypto.newDataKey()
        val data = ByteArray(4096) { 'A'.code.toByte() }
        val sealed = encrypt(key, data)
        assertTrue("plaintext appears in the stored file", !sealed.asList().windowed(64).any { window ->
            window.all { it == 'A'.code.toByte() }
        })
    }

    @Test fun wrongKeyIsRejected() {
        val key = VaultCrypto.newDataKey()
        assertTrue(rejects(VaultCrypto.newDataKey(), encrypt(key, bytes(50_000))))
    }

    @Test fun alteredByteIsRejected() {
        val key = VaultCrypto.newDataKey()
        val sealed = encrypt(key, bytes(50_000))
        sealed[sealed.size / 2] = (sealed[sealed.size / 2].toInt() xor 1).toByte()
        assertTrue(rejects(key, sealed))
    }

    @Test fun truncatedFileIsRejected() {
        val key = VaultCrypto.newDataKey()
        val sealed = encrypt(key, bytes(50_000))
        assertTrue(rejects(key, sealed.copyOf(sealed.size - 4)))
        assertTrue(rejects(key, sealed.copyOf(sealed.size / 2)))
    }

    @Test fun reorderedFramesAreRejected() {
        val key = VaultCrypto.newDataKey()
        val sealed = encrypt(key, bytes(frame + 500))
        val frames = mutableListOf<ByteArray>()
        DataInputStream(ByteArrayInputStream(sealed)).use { input ->
            while (true) {
                val length = input.readInt()
                if (length == -1) break
                frames += ByteArray(length).also { input.readFully(it) }
            }
        }
        assertEquals("expected two frames", 2, frames.size)
        val swapped = ByteArrayOutputStream()
        DataOutputStream(swapped).use { output ->
            output.writeInt(frames[1].size); output.write(frames[1])
            output.writeInt(frames[0].size); output.write(frames[0])
            output.writeInt(-1)
        }
        assertTrue(rejects(key, swapped.toByteArray()))
    }

    @Test fun passwordWrapsAndUnwrapsTheDataKey() {
        val salt = VaultCrypto.randomBytes(VaultCrypto.SALT_BYTES)
        val key = VaultCrypto.deriveKey("correct horse".toCharArray(), salt)
        val dataKey = VaultCrypto.newDataKey()
        val wrapped = VaultCrypto.seal(key, dataKey.encoded)
        assertArrayEquals(dataKey.encoded, VaultCrypto.open(key, wrapped))
        assertArrayEquals(key.encoded, VaultCrypto.deriveKey("correct horse".toCharArray(), salt).encoded)
        try {
            VaultCrypto.open(VaultCrypto.deriveKey("correct horsf".toCharArray(), salt), wrapped)
            fail("a wrong password unwrapped the data key")
        } catch (_: Exception) {
        }
    }

    @Test fun differentSaltsGiveDifferentKeys() {
        val password = "same password"
        val first = VaultCrypto.deriveKey(password.toCharArray(), VaultCrypto.randomBytes(VaultCrypto.SALT_BYTES))
        val second = VaultCrypto.deriveKey(password.toCharArray(), VaultCrypto.randomBytes(VaultCrypto.SALT_BYTES))
        assertNotEquals(first.encoded.toList(), second.encoded.toList())
    }

    @Test fun sameInputEncryptsDifferentlyEachTime() {
        val key = VaultCrypto.newDataKey()
        val data = bytes(10_000)
        assertNotEquals(encrypt(key, data).toList(), encrypt(key, data).toList())
    }
}
