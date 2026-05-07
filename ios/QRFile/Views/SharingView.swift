import SwiftUI

// TODO Phase 2: file picker → build HandshakePayload → display QR code
//               NFC CoreNFC tap-to-send (Phase 3)
//               Wi-Fi Aware send/receive via shared KMP P2PTransport (Phase 2, iOS 19+)
struct SharingView: View {
    var body: some View {
        VStack {
            Text("File Sharing")
                .font(.title2)
            Text("Coming in Phase 2 (iOS 19 + Wi-Fi Aware)")
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding()
        }
    }
}
