import SwiftUI

/// Sender UI: pick files and show handshake JSON (embed KMP `HandshakePayload` when the Xcode project links `shared`).
struct SharingView: View {
    @State private var hint =
        "Wire the Kotlin Multiplatform `shared` framework (Gradle embedAndSignAppleFrameworkForXcode), then build `HandshakePayload` + QR here. Wi‑Fi Aware transport is bridged from Swift / Network.framework on iOS 19+."

    var body: some View {
        VStack(spacing: 16) {
            Text("File Sharing")
                .font(.title2)
            Text(hint)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal)
        }
    }
}
