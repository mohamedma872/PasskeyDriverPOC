package com.example.passkeydriver.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.util.Log
import java.nio.charset.Charset

class NfcManager {

    companion object {
        private const val TAG = "NfcManager"
    }

    fun writeDriverId(tag: Tag, driverId: String): Result<Unit> {
        return try {
            val message = NdefMessage(arrayOf(NdefRecord.createTextRecord("en", driverId)))
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                try {
                    if (!ndef.isWritable) return Result.failure(Exception("Card is read-only"))
                    if (ndef.maxSize < message.byteArrayLength) return Result.failure(Exception("Card too small"))
                    ndef.writeNdefMessage(message)
                    Log.d(TAG, "Written driverId=$driverId to NDEF card")
                    Result.success(Unit)
                } finally {
                    ndef.close()
                }
            } else {
                // Try formatting a blank card
                val formatable = NdefFormatable.get(tag)
                    ?: return Result.failure(Exception("Card does not support NDEF"))
                formatable.connect()
                try {
                    formatable.format(message)
                    Log.d(TAG, "Formatted and written driverId=$driverId")
                    Result.success(Unit)
                } finally {
                    formatable.close()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Write failed", e)
            Result.failure(e)
        }
    }

    fun readDriverId(tag: Tag): Result<String> {
        return try {
            val ndef = Ndef.get(tag)
                ?: return Result.failure(Exception("Card does not contain NDEF data"))
            ndef.connect()
            try {
                val message = ndef.ndefMessage ?: ndef.cachedNdefMessage
                    ?: return Result.failure(Exception("Card is empty"))
                val record = message.records.firstOrNull { r ->
                    r.tnf == NdefRecord.TNF_WELL_KNOWN &&
                        r.type.contentEquals(NdefRecord.RTD_TEXT)
                } ?: return Result.failure(Exception("No text record found on card"))

                val driverId = parseTextRecord(record)
                Log.d(TAG, "Read driverId=$driverId")
                Result.success(driverId)
            } finally {
                ndef.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Read failed", e)
            Result.failure(e)
        }
    }

    // NDEF Text Record format: [status byte][language bytes][text bytes]
    private fun parseTextRecord(record: NdefRecord): String {
        val payload = record.payload
        val statusByte = payload[0].toInt()
        val languageLength = statusByte and 0x3F
        val textStart = 1 + languageLength
        val encoding = if (statusByte and 0x80 != 0) Charsets.UTF_16 else Charsets.UTF_8
        return String(payload, textStart, payload.size - textStart, encoding)
    }
}
