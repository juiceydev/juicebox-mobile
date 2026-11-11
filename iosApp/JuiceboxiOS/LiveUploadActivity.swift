import ActivityKit
import Foundation
import os

private let activityLog = Logger(
    subsystem: "dev.juicey.juicebox",
    category: "live-activity"
)

/// Drives the upload Live Activity: Lock Screen, Notification Centre and the
/// Dynamic Island all render from the same `UploadActivityAttributes`.
final class LiveUploadActivity {
    static let shared = LiveUploadActivity()

    private var activity: Activity<UploadActivityAttributes>?
    private var filename = ""
    private var totalBytes: Int64 = 0
    private var lastReported = -1.0

    private var enabled: Bool { ActivityAuthorizationInfo().areActivitiesEnabled }

    @discardableResult
    func start(filename: String, totalBytes: Int64, server: String) -> Bool {
        guard enabled else {
            activityLog.error("Live Activities are disabled for this app")
            return false
        }
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
        do {
            let activity = try Activity.request(
                attributes: attributes,
                content: ActivityContent(state: state, staleDate: nil),
                pushType: nil
            )
            self.activity = activity
            activityLog.info("Started Live Activity \(activity.id, privacy: .public)")
            return true
        } catch {
            activityLog.error(
                "Activity.request failed: \(String(describing: error), privacy: .public)"
            )
            return false
        }
    }

    /// Fetch progress arrives with the server-reported size only once cobalt
    /// has resolved the link; until then the activity keeps its unknown-size
    /// copy instead of showing "0 bytes".
    func report(progress: Double, totalBytes: Int64) {
        if totalBytes > 0 {
            self.totalBytes = totalBytes
        }
        report(progress: progress)
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
