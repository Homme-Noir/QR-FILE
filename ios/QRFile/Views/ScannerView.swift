import SwiftUI

struct ScannerView: View {
    @State private var status: String = "Scan a QR code or use NFC read."
    @State private var lastPayload: String?
    private let nfc = NfcReader()

    var body: some View {
        VStack(spacing: 16) {
            Text("Scanner")
                .font(.title2)
            Text(status)
                .font(.footnote)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            Button("Read NFC handshake") {
                nfc.onPayload = { text in
                    lastPayload = text
                    status = "Read NFC payload (\(text.count) chars)."
                }
                nfc.onError = { err in
                    status = err
                }
                nfc.start()
            }
            if let lastPayload {
                Text(lastPayload)
                    .font(.caption2)
                    .lineLimit(4)
                    .textSelection(.enabled)
            }
        }
        .padding()
    }
}
