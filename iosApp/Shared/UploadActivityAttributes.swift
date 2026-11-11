import ActivityKit
import Foundation

/// The upload that the app shows on the Lock Screen, in Notification Centre
/// and in the Dynamic Island. Compiled into both the app and the widget.
struct UploadActivityAttributes: ActivityAttributes {

    struct ContentState: Codable, Hashable {
        enum Phase: String, Codable, Hashable {
            case uploading
            case finished
            case failed
        }

        var filename: String
        var progress: Double
        var phase: Phase
        var link: String?
        var bytesSent: Int64
        var totalBytes: Int64
    }

    /// Host of the server the file is going to, e.g. "box.juicey.dev".
    var server: String
}
