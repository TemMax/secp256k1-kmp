/*
 * Copyright 2013 Google Inc.
 * Copyright 2014-2016 the libsecp256k1 contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.bitcoin

import fr.acinq.secp256k1.PubKeyFormat
import fr.acinq.secp256k1.Secp256k1
import fr.acinq.secp256k1.SigFormat
import org.bitcoin.NativeSecp256k1Util.AssertFailException
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 *
 * This class holds native methods to handle ECDSA verification.
 *
 *
 * You can find an example library that can be used for this at https://github.com/bitcoin/secp256k1
 *
 *
 * To build secp256k1 for use with bitcoinj, run
 * `./configure --enable-jni --enable-experimental --enable-module-ecdh`
 * and `make` then copy `.libs/libsecp256k1.so` to your system library path
 * or point the JVM to the folder containing it with -Djava.library.path
 *
 */
public object NativeSecp256k1 : Secp256k1 {
    private val rwl = ReentrantReadWriteLock()
    private val r: Lock = rwl.readLock()
    private val w: Lock = rwl.writeLock()
    private val nativeECDSABuffer = ThreadLocal<ByteBuffer?>()

    private fun pack(vararg buffers: ByteArray): ByteBuffer {
        var size = 0
        for (i in buffers.indices) {
            size += buffers[i].size
        }

        val byteBuff = nativeECDSABuffer.get()?.takeIf { it.capacity() >= size }
            ?: ByteBuffer.allocateDirect(size).also {
                it.order(ByteOrder.nativeOrder())
                nativeECDSABuffer.set(it)
            }
        byteBuff.rewind()
        for (i in buffers.indices) {
            byteBuff.put(buffers[i])
        }
        return byteBuff
    }

    /**
     * Verifies the given secp256k1 signature in native code.
     * Calling when enabled == false is undefined (probably library not loaded)
     *
     * @param data      The data which was signed, must be exactly 32 bytes
     * @param signature The signature
     * @param pub       The public key which did the signing
     * @return true if the signature is valid
     * @throws AssertFailException in case of failure
     */
    @Throws(AssertFailException::class)
    override fun verify(data: ByteArray, signature: ByteArray, pub: ByteArray): Boolean {
        require(data.size == 32 && signature.size <= 520 && pub.size <= 520)
        val byteBuff = pack(data, signature, pub)
        r.lock()
        return try {
            Secp256k1CFunctions.secp256k1_ecdsa_verify(
                byteBuff,
                Secp256k1Context.getContext(),
                signature.size,
                pub.size
            ) == 1
        } finally {
            r.unlock()
        }
    }

    /**
     * libsecp256k1 Create an ECDSA signature.
     *
     * @param data    Message hash, 32 bytes
     * @param sec     Secret key, 32 bytes
     * @param compact True for compact signature, false for DER
     * @return a signature, or an empty array is signing failed
     * @throws AssertFailException in case of failure
     */
    @Throws(AssertFailException::class)
    override fun sign(data: ByteArray, sec: ByteArray, format: SigFormat): ByteArray {
        require(data.size == 32 && sec.size <= 32)
        val byteBuff = pack(data, sec)
        val retByteArray: Array<ByteArray>
        r.lock()
        retByteArray = try {
            Secp256k1CFunctions.secp256k1_ecdsa_sign(
                byteBuff,
                format == SigFormat.COMPACT,
                Secp256k1Context.getContext()
            )
        } finally {
            r.unlock()
        }
        val sigArr = retByteArray[0]
        val sigLen = BigInteger(byteArrayOf(retByteArray[1][0])).toInt()
        val retVal = BigInteger(byteArrayOf(retByteArray[1][1])).toInt()
        NativeSecp256k1Util.assertEquals(
            sigArr.size,
            sigLen,
            "Got bad signature length."
        )
        return if (retVal == 0) ByteArray(0) else sigArr
    }

    @Throws(AssertFailException::class)
    override fun signatureNormalize(sig: ByteArray, format: SigFormat): Pair<ByteArray, Boolean> {
        require(sig.size == 64 || sig.size in 70..73)
        val byteBuff = pack(sig)
        val retByteArray: Array<ByteArray>
        r.lock()
        retByteArray = try {
            Secp256k1CFunctions.secp256k1_ecdsa_normalize(
                byteBuff,
                sig.size,
                format == SigFormat.COMPACT,
                Secp256k1Context.getContext()
            )
        } finally {
            r.unlock()
        }
        val sigArr = retByteArray[0]
        val sigLen = BigInteger(byteArrayOf(retByteArray[1][0])).toInt()
        val retVal = BigInteger(byteArrayOf(retByteArray[1][1])).toInt()
        val retBool = BigInteger(byteArrayOf(retByteArray[1][2])).toInt()
        NativeSecp256k1Util.assertEquals(
            sigArr.size,
            sigLen,
            "Got bad signature length."
        )
        return (if (retVal == 0) ByteArray(0) else sigArr) to (retBool == 1)
    }

    /**
     * libsecp256k1 Seckey Verify - returns 1 if valid, 0 if invalid
     *
     * @param seckey ECDSA Secret key, 32 bytes
     * @return true if seckey is valid
     */
    override fun secKeyVerify(seckey: ByteArray): Boolean {
        require(seckey.size == 32)
        val byteBuff = pack(seckey)
        r.lock()
        return try {
            Secp256k1CFunctions.secp256k1_ec_seckey_verify(
                byteBuff,
                Secp256k1Context.getContext()
            ) == 1
        } finally {
            r.unlock()
        }
    }

    /**
     * libsecp256k1 Compute Pubkey - computes public key from secret key
     *
     * @param seckey ECDSA Secret key, 32 bytes
     * @throws AssertFailException if parameters are not valid
     * @return the corresponding public key (uncompressed)
     */
    //TODO add a 'compressed' arg
    @Throws(AssertFailException::class)
    override fun computePubkey(seckey: ByteArray, format: PubKeyFormat): ByteArray {
        require(seckey.size == 32)
        val byteBuff = pack(seckey)
        val retByteArray: Array<ByteArray>
        r.lock()
        retByteArray = try {
            Secp256k1CFunctions.secp256k1_ec_pubkey_create(
                byteBuff,
                format == PubKeyFormat.COMPRESSED,
                Secp256k1Context.getContext()
            )
        } finally {
            r.unlock()
        }
        val pubArr = retByteArray[0]
        val pubLen = BigInteger(byteArrayOf(retByteArray[1][0])).toInt()
        val retVal = BigInteger(byteArrayOf(retByteArray[1][1])).toInt()
        NativeSecp256k1Util.assertEquals(pubArr.size, pubLen, "Got bad pubkey length.")
        return if (retVal == 0) ByteArray(0) else pubArr
    }

    /**
     * @param pubkey public key
     * @return the input public key (uncompressed) if valid, or an empty array
     * @throws AssertFailException in case of failure
     */
    @Throws(AssertFailException::class)
    override fun parsePubkey(pubkey: ByteArray, format: PubKeyFormat): ByteArray {
        require(pubkey.size == 33 || pubkey.size == 65)
        val byteBuff = pack(pubkey)
        val retByteArray: Array<ByteArray>
        r.lock()
        retByteArray = try {
            Secp256k1CFunctions.secp256k1_ec_pubkey_parse(
                byteBuff,
                Secp256k1Context.getContext(),
                pubkey.size,
                format == PubKeyFormat.COMPRESSED
            )
        } finally {
            r.unlock()
        }
        val pubArr = retByteArray[0]
        BigInteger(byteArrayOf(retByteArray[1][0])).toInt()
        val retVal = BigInteger(byteArrayOf(retByteArray[1][1])).toInt()
        NativeSecp256k1Util.assertEquals(
            pubArr.size,
            format.maxSize,
            "Got bad pubkey length."
        )
        return if (retVal == 0) ByteArray(0) else pubArr
    }

    /**
     * libsecp256k1 Cleanup - This destroys the secp256k1 context object
     * This should be called at the end of the program for proper cleanup of the context.
     */
    @Synchronized
    override fun cleanup() {
        w.lock()
        try {
            Secp256k1CFunctions.secp256k1_destroy_context(Secp256k1Context.getContext())
        } finally {
            w.unlock()
        }
    }

    @Throws(AssertFailException::class)
    override fun privKeyNegate(privkey: ByteArray): ByteArray {
        require(privkey.size == 32)
        val byteBuff = pack(privkey)
        val retByteArray: Array<ByteArray>
        r.lock()
        retByteArray = try {
            Secp256k1CFunctions.secp256k1_privkey_negate(
                byteBuff,
                Secp256k1Context.getContext()
            )
        } finally {
            r.unlock()
        }
        val privArr = retByteArray[0]
        val privLen: Int = BigInteger(byteArrayOf(retByteArray[1][0])).toInt() and 0xFF
        val retVal = BigInteger(byteArrayOf(retByteArray[1][1])).toInt()
        NativeSecp256k1Util.assertEquals(
            privArr.size,
            privLen,
            "Got bad privkey length."
        )
        NativeSecp256k1Util.assertEquals(retVal, 1, "Failed return value check.")
        return privArr
    }

    /**
     * libsecp256k1 PrivKey Tweak-Mul - Tweak privkey by multiplying to it
     *
     * @param privkey 32-byte seckey
     * @param tweak   some bytes to tweak with
     * @return privkey * tweak
     * @throws AssertFailException in case of failure
     */
    @Throws(AssertFailException::class)
    override fun privKeyTweakMul(privkey: ByteArray, tweak: ByteArray): ByteArray {
        require(privkey.size == 32)
        val byteBuff = pack(privkey, tweak)
        val retByteArray: Array<ByteArray>
        r.lock()
        retByteArray = try {
            Secp256k1CFunctions.secp256k1_privkey_tweak_mul(
                byteBuff,
                Secp256k1Context.getContext()
            )
        } finally {
            r.unlock()
        }
        val privArr = retByteArray[0]
        val privLen: Int = BigInteger(byteArrayOf(retByteArray[1][0])).toInt() and 0xFF
        val retVal = BigInteger(byteArrayOf(retByteArray[1][1])).toInt()
        NativeSecp256k1Util.assertEquals(
            privArr.size,
            privLen,
            "Got bad privkey length."
        )
        NativeSecp256k1Util.assertEquals(retVal, 1, "Failed return value check.")
        return privArr
    }

    /**
     * libsecp256k1 PrivKey Tweak-Add - Tweak privkey by adding to it
     *
     * @param privkey 32-byte seckey
     * @param tweak  some bytes to tweak with
     * @return privkey + tweak
     * @throws AssertFailException in case of failure
     */
    @Throws(AssertFailException::class)
    override fun privKeyTweakAdd(privkey: ByteArray, tweak: ByteArray): ByteArray {
        require(privkey.size == 32)
        val byteBuff = pack(privkey, tweak)
        val retByteArray: Array<ByteArray>
        r.lock()
        retByteArray = try {
            Secp256k1CFunctions.secp256k1_privkey_tweak_add(
                byteBuff,
                Secp256k1Context.getContext()
            )
        } finally {
            r.unlock()
        }
        val privArr = retByteArray[0]
        val privLen: Int = BigInteger(byteArrayOf(retByteArray[1][0])).toInt() and 0xFF
        val retVal = BigInteger(byteArrayOf(retByteArray[1][1])).toInt()
        NativeSecp256k1Util.assertEquals(privArr.size, privLen, "Got bad pubkey length.")
        NativeSecp256k1Util.assertEquals(retVal, 1, "Failed return value check.")
        return privArr
    }

    @Throws(AssertFailException::class)
    override fun pubKeyNegate(pubkey: ByteArray): ByteArray {
        require(pubkey.size == 33 || pubkey.size == 65)
        val byteBuff = pack(pubkey)
        val retByteArray: Array<ByteArray>
        r.lock()
        retByteArray = try {
            Secp256k1CFunctions.secp256k1_pubkey_negate(
                byteBuff,
                Secp256k1Context.getContext(),
                pubkey.size
            )
        } finally {
            r.unlock()
        }
        val pubArr = retByteArray[0]
        val pubLen: Int = BigInteger(byteArrayOf(retByteArray[1][0])).toInt() and 0xFF
        val retVal = BigInteger(byteArrayOf(retByteArray[1][1])).toInt()
        NativeSecp256k1Util.assertEquals(pubArr.size, pubLen, "Got bad pubkey length.")
        NativeSecp256k1Util.assertEquals(retVal, 1, "Failed return value check.")
        return pubArr
    }

    /**
     * libsecp256k1 PubKey Tweak-Add - Tweak pubkey by adding to it
     *
     * @param tweak  some bytes to tweak with
     * @param pubkey 32-byte seckey
     * @return pubkey + tweak
     * @throws AssertFailException in case of failure
     */
    @Throws(AssertFailException::class)
    override fun pubKeyTweakAdd(pubkey: ByteArray, tweak: ByteArray): ByteArray {
        require(pubkey.size == 33 || pubkey.size == 65)
        val byteBuff = pack(pubkey, tweak)
        val retByteArray: Array<ByteArray>
        r.lock()
        retByteArray = try {
            Secp256k1CFunctions.secp256k1_pubkey_tweak_add(
                byteBuff,
                Secp256k1Context.getContext(),
                pubkey.size
            )
        } finally {
            r.unlock()
        }
        val pubArr = retByteArray[0]
        val pubLen: Int = BigInteger(byteArrayOf(retByteArray[1][0])).toInt() and 0xFF
        val retVal = BigInteger(byteArrayOf(retByteArray[1][1])).toInt()
        NativeSecp256k1Util.assertEquals(pubArr.size, pubLen, "Got bad pubkey length.")
        NativeSecp256k1Util.assertEquals(retVal, 1, "Failed return value check.")
        return pubArr
    }

    /**
     * libsecp256k1 PubKey Tweak-Mul - Tweak pubkey by multiplying to it
     *
     * @param tweak  some bytes to tweak with
     * @param pubkey 32-byte seckey
     * @return pubkey * tweak
     * @throws AssertFailException in case of failure
     */
    @Throws(AssertFailException::class)
    override fun pubKeyTweakMul(pubkey: ByteArray, tweak: ByteArray): ByteArray {
        require(pubkey.size == 33 || pubkey.size == 65)
        val byteBuff = pack(pubkey, tweak)
        val retByteArray: Array<ByteArray>
        r.lock()
        retByteArray = try {
            Secp256k1CFunctions.secp256k1_pubkey_tweak_mul(
                byteBuff,
                Secp256k1Context.getContext(),
                pubkey.size
            )
        } finally {
            r.unlock()
        }
        val pubArr = retByteArray[0]
        val pubLen: Int = BigInteger(byteArrayOf(retByteArray[1][0])).toInt() and 0xFF
        val retVal = BigInteger(byteArrayOf(retByteArray[1][1])).toInt()
        NativeSecp256k1Util.assertEquals(pubArr.size, pubLen, "Got bad pubkey length.")
        NativeSecp256k1Util.assertEquals(retVal, 1, "Failed return value check.")
        return pubArr
    }

    @Throws(AssertFailException::class)
    override fun pubKeyAdd(pubkey1: ByteArray, pubkey2: ByteArray): ByteArray {
        require(pubkey1.size == 33 || pubkey1.size == 65)
        require(pubkey2.size == 33 || pubkey2.size == 65)
        val byteBuff = pack(pubkey1, pubkey2)
        val retByteArray: Array<ByteArray>
        r.lock()
        retByteArray = try {
            Secp256k1CFunctions.secp256k1_ec_pubkey_add(
                byteBuff,
                Secp256k1Context.getContext(),
                pubkey1.size,
                pubkey2.size
            )
        } finally {
            r.unlock()
        }
        val pubArr = retByteArray[0]
        val pubLen: Int = BigInteger(byteArrayOf(retByteArray[1][0])).toInt() and 0xFF
        val retVal = BigInteger(byteArrayOf(retByteArray[1][1])).toInt()
        NativeSecp256k1Util.assertEquals(pubkey1.size, pubLen, "Got bad pubkey length.")
        NativeSecp256k1Util.assertEquals(retVal, 1, "Failed return value check.")
        return pubArr
    }

    /**
     * libsecp256k1 create ECDH secret - constant time ECDH calculation
     *
     * @param seckey byte array of secret key used in exponentiaion
     * @param pubkey byte array of public key used in exponentiaion
     * @return ecdh(sedckey, pubkey)
     * @throws AssertFailException in case of failure
     */
    @Throws(AssertFailException::class)
    override fun createECDHSecret(seckey: ByteArray, pubkey: ByteArray): ByteArray {
        require(seckey.size <= 32 && pubkey.size <= 65)
        val byteBuff = pack(seckey, pubkey)
        val retByteArray: Array<ByteArray>
        r.lock()
        retByteArray = try {
            Secp256k1CFunctions.secp256k1_ecdh(
                byteBuff,
                Secp256k1Context.getContext(),
                pubkey.size
            )
        } finally {
            r.unlock()
        }
        val resArr = retByteArray[0]
        val retVal = BigInteger(byteArrayOf(retByteArray[1][0])).toInt()
        NativeSecp256k1Util.assertEquals(resArr.size, 32, "Got bad result length.")
        NativeSecp256k1Util.assertEquals(retVal, 1, "Failed return value check.")
        return resArr
    }

    @Throws(AssertFailException::class)
    override fun ecdsaRecover(sig: ByteArray, message: ByteArray, recid: Int, format: PubKeyFormat): ByteArray {
        require(sig.size == 64)
        require(message.size == 32)
        val byteBuff = pack(sig, message)
        val retByteArray: Array<ByteArray>
        r.lock()
        retByteArray = try {
            Secp256k1CFunctions.secp256k1_ecdsa_recover(
                byteBuff,
                Secp256k1Context.getContext(),
                recid,
                format == PubKeyFormat.COMPRESSED
            )
        } finally {
            r.unlock()
        }
        val resArr = retByteArray[0]
        val retVal = BigInteger(byteArrayOf(retByteArray[1][0])).toInt()
        NativeSecp256k1Util.assertEquals(
            resArr.size,
            format.maxSize,
            "Got bad result length."
        )
        NativeSecp256k1Util.assertEquals(retVal, 1, "Failed return value check.")
        return resArr
    }

    /**
     * libsecp256k1 randomize - updates the context randomization
     *
     * @param seed 32-byte random seed
     * @return true if successful
     * @throws AssertFailException in case of failure
     */
    @Synchronized
    @Throws(AssertFailException::class)
    override fun randomize(seed: ByteArray): Boolean {
        require(seed.size == 32)
        val byteBuff = pack(seed)
        w.lock()
        return try {
            Secp256k1CFunctions.secp256k1_context_randomize(
                byteBuff,
                Secp256k1Context.getContext()
            ) == 1
        } finally {
            w.unlock()
        }
    }
}