import Foundation

/// One upload started from this device, kept for the length of a session so
/// the Upload tab can show what happened even after the result card clears.
struct UploadSessionItem: Identifiable, Codable {
    let id: String
    let name: String
    let startedAt: Date
    var status: String
    var fraction: Double
    var bytesSent: Int64
    var sizeBytes: Int64
    var speed: Double
    var url: String? = nil
    var rawUrl: String? = nil
    var downloadUrl: String? = nil
    var deleteToken: String? = nil
    var libraryId: String? = nil
    var error: String? = nil

    var isActive: Bool { status == "queued" || status == "uploading" }
    var isDone: Bool { status == "done" }
}

enum UploadSessionStore {
    private static let key = "juicebox.session.uploads"
    private static let window: TimeInterval = 15 * 60

    static func load() -> [UploadSessionItem] {
        guard let data = UserDefaults.standard.data(forKey: key) else { return [] }
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        guard let decoded = try? decoder.decode([UploadSessionItem].self, from: data)
        else { return [] }
        return prune(decoded)
    }

    /// Fixed entries for screenshot runs, so the session sheet can be captured
    /// without waiting for a real transfer.
    static func demoItems() -> [UploadSessionItem] {
        let now = Date()
        let size: Int64 = 248 * 1024 * 1024
        return [
            UploadSessionItem(
                id: "demo-session-1",
                name: "holiday-clip.mp4",
                startedAt: now.addingTimeInterval(-48),
                status: "done",
                fraction: 1,
                bytesSent: size,
                sizeBytes: size,
                speed: 61 * 1024 * 1024,
                url: "https://f.juicey.dev/v/demo0001.mp4",
                rawUrl: "https://f.juicey.dev/f/demo0001.mp4",
                downloadUrl: "https://f.juicey.dev/d/demo0001.mp4"
            ),
            UploadSessionItem(
                id: "demo-session-2",
                name: "mixdown.wav",
                startedAt: now.addingTimeInterval(-190),
                status: "done",
                fraction: 1,
                bytesSent: 34 * 1024 * 1024,
                sizeBytes: 34 * 1024 * 1024,
                speed: 28 * 1024 * 1024,
                url: "https://f.juicey.dev/v/demo0002.wav",
                rawUrl: "https://f.juicey.dev/f/demo0002.wav",
                downloadUrl: "https://f.juicey.dev/d/demo0002.wav"
            ),
        ]
    }

    static func save(_ items: [UploadSessionItem]) {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        guard let data = try? encoder.encode(prune(items)) else { return }
        UserDefaults.standard.set(data, forKey: key)
    }

    /// Active transfers always stay; finished ones age out after 15 minutes,
    /// matching the window the Android session sheet uses.
    static func prune(
        _ items: [UploadSessionItem],
        now: Date = Date()
    ) -> [UploadSessionItem] {
        items.filter { item in
            item.isActive || now.timeIntervalSince(item.startedAt) < window
        }
    }
}
