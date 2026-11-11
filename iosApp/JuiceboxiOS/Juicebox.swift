import Foundation
import JuiceboxCore

enum JuiceboxRuntime {
    static let version: String =
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0"

    /// One bridge per process. The SwiftUI app, App Intents and the live
    /// activity all share this session, settings and library.
    static let bridge = IosBridge(version: version)
}
