import CoreNFC
import Foundation

/// Reads the first text payload from an NDEF tag (same JSON as QR handshake).
final class NfcReader: NSObject, NFCNDEFReaderSessionDelegate {
    var onPayload: ((String) -> Void)?
    var onError: ((String) -> Void)?
    private var session: NFCNDEFReaderSession?

    func start() {
        guard NFCNDEFReaderSession.readingAvailable else {
            onError?("NFC reading is not available on this device.")
            return
        }
        let s = NFCNDEFReaderSession(delegate: self, queue: nil, invalidateAfterFirstRead: false)
        session = s
        s.alertMessage = "Hold near the sender's NFC tag."
        s.begin()
    }

    func readerSession(_ session: NFCNDEFReaderSession, didInvalidateWithError error: Error) {
        onError?(error.localizedDescription)
    }

    func readerSession(_ session: NFCNDEFReaderSession, didDetectNDEFs messages: [NFCNDEFMessage]) {
        for message in messages {
            for record in message.records {
                if let s = String(data: record.payload, encoding: .utf8), !s.isEmpty {
                    onPayload?(s)
                    session.invalidate()
                    return
                }
            }
        }
        onError?("No readable text on tag.")
        session.invalidate()
    }
}
