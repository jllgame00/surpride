package com.surpride.app.experience

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.surpride.app.experience.core.*
import java.io.*
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Local encrypted DEMO preferences/history, excluded from backups by noBackupFilesDir. No logs/export. */
class DemoStateStore(context: Context) {
    private val file=File(context.noBackupFilesDir,"surpride-demo-v1.bin")
    private val atomic=AtomicFile(file)
    private fun key(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
        return store.getKey("surpride_demo_state_v1",null) as? SecretKey ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("surpride_demo_state_v1",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun load(): MysteryTripState? {
        if(!file.exists() && !File(file.path+".bak").exists()) return null
        require(file.length() <= 300_000)
        return atomic.openRead().use { raw -> DataInputStream(raw).use { input ->
            require(input.readInt()==1)
            val size=input.readInt().also {require(it in 12..32)}
            val iv=ByteArray(size);input.readFully(iv)
            val encrypted=input.readBytes()
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,iv))
            TripSnapshotCodec.decode(cipher.doFinal(encrypted),DemoCatalog.candidates)
        }}
    }
    fun save(state: MysteryTripState) {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.ENCRYPT_MODE,key())}
        val encrypted=cipher.doFinal(TripSnapshotCodec.encode(state))
        val output=atomic.startWrite()
        try {
            val data=DataOutputStream(output)
            data.writeInt(1);data.writeInt(cipher.iv.size);data.write(cipher.iv);data.write(encrypted);data.flush()
            atomic.finishWrite(output)
        } catch (e: Exception) {atomic.failWrite(output);throw e}
    }
    fun reset() {atomic.delete()}
}
