package com.lolokeksu.gallery

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-GCM storage for the private vault.
 *
 * The password never encrypts media directly. A random data key encrypts the files and is
 * itself wrapped with a key derived from the password, so changing the password rewraps one
 * small blob instead of rewriting every file.
 *
 * Media is written as independent 1 MiB frames rather than one GCM stream. That bounds memory
 * no matter how a provider buffers, and each frame carries its index as associated data, so a
 * reordered or spliced file fails to decrypt instead of quietly returning wrong bytes. A
 * terminator frame marks a complete file, which turns truncation into an error rather than a
 * silently short photo.
 */
object VaultCrypto {
    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val FRAME_BYTES = 1 shl 20
    private const val TERMINATOR = -1
    const val SALT_BYTES = 16

    class VaultDataException(message: String) : Exception(message)

    /**
     * What a stream actually carried. The byte count matters as much as the digest: a source
     * that ends early hashes consistently with what was written, so only the count can show
     * that the copy is short.
     */
    class StreamResult(val sha256: ByteArray, val bytes: Long)

    fun randomBytes(count: Int): ByteArray = ByteArray(count).also { SecureRandom().nextBytes(it) }

    fun deriveKey(password: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun newDataKey(): SecretKey = SecretKeySpec(randomBytes(KEY_BITS / 8), "AES")

    fun keyOf(raw: ByteArray): SecretKey = SecretKeySpec(raw, "AES")

    /** Small payloads: metadata, thumbnails and the wrapped data key. Layout is IV || ciphertext. */
    fun seal(key: SecretKey, plaintext: ByteArray): ByteArray {
        val iv = randomBytes(IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return iv + cipher.doFinal(plaintext)
    }

    /** Throws when the key is wrong or the bytes were altered; that is how a wrong password is detected. */
    fun open(key: SecretKey, sealed: ByteArray): ByteArray {
        if (sealed.size <= IV_BYTES) throw VaultDataException("sealed payload is too short")
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, sealed.copyOf(IV_BYTES)))
        return cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
    }

    /**
     * Encrypts [input] into [output] and returns the SHA-256 of the plaintext that was read,
     * so the caller can prove the copy is sound before the original is touched.
     */
    fun encryptStream(key: SecretKey, input: InputStream, output: OutputStream): StreamResult {
        val digest = MessageDigest.getInstance("SHA-256")
        val plain = ByteArray(FRAME_BYTES)
        var index = 0
        var total = 0L
        while (true) {
            val read = fill(input, plain)
            if (read == 0) break
            digest.update(plain, 0, read)
            total += read
            writeFrame(output, sealFrame(key, index, plain, read))
            index++
            if (read < plain.size) break
        }
        writeInt(output, TERMINATOR)
        return StreamResult(digest.digest(), total)
    }

    /**
     * Decrypts into [output], or verifies without writing when [output] is null, and returns the
     * SHA-256 of the plaintext.
     */
    fun decryptStream(key: SecretKey, input: InputStream, output: OutputStream?): StreamResult {
        val digest = MessageDigest.getInstance("SHA-256")
        var index = 0
        var total = 0L
        while (true) {
            val length = readInt(input)
            if (length == TERMINATOR) break
            if (length <= IV_BYTES || length > FRAME_BYTES + IV_BYTES + TAG_BITS / 8) {
                throw VaultDataException("vault file frame length is out of range")
            }
            val frame = ByteArray(length)
            if (fill(input, frame) != length) throw VaultDataException("vault file is truncated")
            val plain = openFrame(key, index, frame)
            digest.update(plain)
            total += plain.size
            output?.write(plain)
            index++
        }
        return StreamResult(digest.digest(), total)
    }

    private fun sealFrame(key: SecretKey, index: Int, plain: ByteArray, length: Int): ByteArray {
        val iv = randomBytes(IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(indexBytes(index))
        return iv + cipher.doFinal(plain, 0, length)
    }

    private fun openFrame(key: SecretKey, index: Int, frame: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, frame.copyOf(IV_BYTES)))
        cipher.updateAAD(indexBytes(index))
        return cipher.doFinal(frame, IV_BYTES, frame.size - IV_BYTES)
    }

    private fun indexBytes(index: Int) = byteArrayOf(
        (index ushr 24).toByte(), (index ushr 16).toByte(), (index ushr 8).toByte(), index.toByte()
    )

    private fun writeFrame(output: OutputStream, frame: ByteArray) {
        writeInt(output, frame.size)
        output.write(frame)
    }

    private fun writeInt(output: OutputStream, value: Int) = output.write(indexBytes(value))

    private fun readInt(input: InputStream): Int {
        val header = ByteArray(4)
        val read = fill(input, header)
        if (read == 0) throw VaultDataException("vault file ends without a terminator")
        if (read != 4) throw EOFException("vault file header is truncated")
        return (header[0].toInt() and 0xFF shl 24) or (header[1].toInt() and 0xFF shl 16) or
            (header[2].toInt() and 0xFF shl 8) or (header[3].toInt() and 0xFF)
    }

    /** Reads until [target] is full or the stream ends; returns how many bytes were read. */
    private fun fill(input: InputStream, target: ByteArray): Int {
        var filled = 0
        while (filled < target.size) {
            val read = input.read(target, filled, target.size - filled)
            if (read < 0) break
            filled += read
        }
        return filled
    }
}
