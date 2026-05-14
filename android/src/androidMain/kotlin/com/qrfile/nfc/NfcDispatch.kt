package com.qrfile.nfc

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.os.Build
import com.qrfile.MainActivity

object NfcDispatch {
    fun createPendingIntent(activity: MainActivity): PendingIntent =
        PendingIntent.getActivity(
            activity,
            0,
            Intent(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or pendingImmutable(),
        )

    private fun pendingImmutable(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    @SuppressLint("ObsoleteSdkInt")
    fun extractText(intent: Intent): String? {
        if (intent.action != NfcAdapter.ACTION_NDEF_DISCOVERED &&
            intent.action != NfcAdapter.ACTION_TECH_DISCOVERED
        ) {
            return null
        }
        val raw = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES) ?: return null
        for (parcelable in raw) {
            val ndef = parcelable as? NdefMessage ?: continue
            for (rec in ndef.records) {
                if (rec.tnf == NdefRecord.TNF_WELL_KNOWN && rec.type.contentEquals(NdefRecord.RTD_TEXT)) {
                    val payload = rec.payload
                    if (payload.isEmpty()) continue
                    val langLen = (payload[0].toInt() and 0x3F)
                    val textBytes = payload.copyOfRange(1 + langLen, payload.size)
                    return String(textBytes, Charsets.UTF_8)
                }
            }
        }
        return null
    }
}
