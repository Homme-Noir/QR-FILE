import SwiftUI

// TODO Phase 1: AVCaptureSession-based QR/barcode scanner
// On successful decode: categorise result, save ScanRecord via shared KMP storage module
struct ScannerView: View {
    var body: some View {
        VStack {
            Text("Scanner")
                .font(.title2)
            Text("Coming in Phase 1")
                .foregroundStyle(.secondary)
        }
    }
}
