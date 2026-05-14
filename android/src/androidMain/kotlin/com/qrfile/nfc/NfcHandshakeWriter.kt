package com.qrfile.nfc

import android.app.Activity
import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import java.io.IOException

/**
 * Writes handshake JSON as an NDEF MIME record. Uses reader/writer mode so the UI can stay in the foreground.
 */
object NfcHandshakeWriter {

    fun writeJsonToTag(activity: Activity, json: String, onDone: (Result<Unit>) -> Unit) {
        val adapter = NfcAdapter.getDefaultAdapter(activity)
        if (adapter == null) {
            onDone(Result.failure(IllegalStateException("NFC not available on this device")))
            return
        }
        if (!adapter.isEnabled) {
            onDone(Result.failure(IllegalStateException("Turn on NFC in system settings")))
            return
        }

        // RTD_TEXT so [NfcDispatch.extractText] matches cold-tap dispatch (same payload as QR JSON).
        val record = NdefRecord.createTextRecord("en", json)
        val message = NdefMessage(arrayOf(record))

        val flags =
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NFC_BARCODE or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK

        val callback = NfcAdapter.ReaderCallback { tag ->
            val result = runCatching { writeNdefToTag(tag, message) }
            activity.runOnUiThread {
                adapter.disableReaderMode(activity)
                onDone(result)
            }
        }

        adapter.enableReaderMode(activity, callback, flags, null)
    }

    @Throws(IOException::class, FormatException::class)
    private fun writeNdefToTag(tag: Tag, message: NdefMessage) {
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            ndef.connect()
            try {
                if (!ndef.isWritable) error("Tag is read-only")
                val max = ndef.maxSize
                if (message.byteArrayLength > max) {
                    error("Handshake too large for this tag ($max bytes max)")
                }
                ndef.writeNdefMessage(message)
            } finally {
                runCatching { ndef.close() }
            }
            return
        }
        val formatable = NdefFormatable.get(tag)
            ?: error("Tag does not support NDEF")
        formatable.connect()
        try {
            formatable.format(message)
        } finally {
            runCatching { formatable.close() }
        }
    }
}
