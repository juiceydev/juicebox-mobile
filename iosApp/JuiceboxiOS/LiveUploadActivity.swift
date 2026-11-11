import ActivityKit
import Foundation

/// Drives the upload Live Activity: Lock Screen, Notification Centre and the
/// Dynamic Island all render from the same `UploadActivityAttributes`.
final class LiveUploadActivity {
    static let shared = LiveUploadActivity()

    private var activity: Activity<UploadActivityAttributes>?
    private var filename = ""
    private var totalBytes: Int64 = 0
    private var lastReported = -1.0

    private var enabled: Bool { ActivityAuthorizationInfo().areActivitiesEnabled }

    func start(filename: String, totalBytes: Int64, server: String) {
        guard enabled else { return }
        endStale()
        self.filename = filename.isEmpty ? "Upload" : filename
        self.totalBytes = totalBytes
        lastReported = 0

        let attributes = UploadActivityAttributes(server: server.isEmpty ? "Juicebox" : server)
        let state = UploadActivityAttributes.ContentState(
            filename: self.filename,
            progress: 0,
            phase: .uploading,
            link: nil,
            bytesSent: 0,
            totalBytes: totalBytes
        )
        activity = try? Activity.request(
            attributes: attributes,
            content: ActivityContent(state: state, staleDate: nil),
            pushType: nil
        )
    }

    func report(progress: Double) {
        guard let activity else { return }
        let clamped = min(1, max(0, progress))
        guard clamped - lastReported >= 0.02 || clamped >= 1 else { return }
        lastReported = clamped
        let state = UploadActivityAttributes.ContentState(
            filename: filename,
            progress: clamped,
            phase: .uploading,
            link: nil,
            bytesSent: Int64(Double(totalBytes) * clamped),
            totalBytes: totalBytes
        )
        Task { await activity.update(ActivityContent(state: state, staleDate: nil)) }
    }

    func finish(link: String?) {
        guard let activity else { return }
        let state = UploadActivityAttributes.ContentState(
            filename: filename,
            progress: 1,
            phase: .finished,
            link: link,
            bytesSent: totalBytes,
            totalBytes: totalBytes
        )
        self.activity = nil
        Task { await activity.end(ActivityContent(state: state, staleDate: nil), dismissalPolicy: .default) }
    }

    func fail() {
        guard let activity else { return }
        let state = UploadActivityAttributes.ContentState(
            filename: filename,
            progress: lastReported < 0 ? 0 : lastReported,
            phase: .failed,
            link: nil,
            bytesSent: 0,
            totalBytes: totalBytes
        )
        self.activity = nil
        Task { await activity.end(ActivityContent(state: state, staleDate: nil), dismissalPolicy: .immediate) }
    }

    private func endStale() {
        guard let activity else { return }
        self.activity = nil
        Task { await activity.end(nil, dismissalPolicy: .immediate) }
    }
}
