import AppIntents
import Foundation
import JuiceboxCore
import UniformTypeIdentifiers

enum UploadServiceError: LocalizedError {
    case emptyFile
    case failed(String)
    case noLink

    var errorDescription: String? {
        switch self {
        case .emptyFile: return "There was nothing to upload."
        case .failed(let message): return message
        case .noLink: return "The server returned no link."
        }
    }
}

/// Uploads on behalf of App Intents. Reuses the app's single bridge, so the
/// session, retention and password settings are the ones in Settings.
enum UploadService {

    static func uploadFile(_ file: IntentFile) async throws -> URL {
        let name = file.filename.isEmpty ? "upload" : file.filename
        let mime = file.type?.preferredMIMEType ?? "application/octet-stream"
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("juicebox-intent-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }

        let target = directory.appendingPathComponent(name)
        try file.data.write(to: target)
        return try await upload(path: target.path, filename: name, mime: mime)
    }

    static func uploadText(_ text: String) async throws -> URL {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { throw UploadServiceError.emptyFile }
        let file = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<LibraryFile, Error>) in
            JuiceboxRuntime.bridge.saveText(
                text: text,
                ttlMinutes: JuiceboxRuntime.bridge.defaultTtlMinutes(),
                onProgress: { _ in },
                completion: { file, error in
                    if let file {
                        continuation.resume(returning: file)
                    } else {
                        continuation.resume(throwing: UploadServiceError.failed(error ?? "Upload failed"))
                    }
                }
            )
        }
        return try link(for: file)
    }

    private static func upload(path: String, filename: String, mime: String) async throws -> URL {
        let file = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<LibraryFile, Error>) in
            JuiceboxRuntime.bridge.uploadFile(
                path: path,
                filename: filename,
                mimeType: mime,
                ttlMinutes: JuiceboxRuntime.bridge.defaultTtlMinutes(),
                onProgress: { _ in },
                completion: { file, error in
                    if let file {
                        continuation.resume(returning: file)
                    } else {
                        continuation.resume(throwing: UploadServiceError.failed(error ?? "Upload failed"))
                    }
                }
            )
        }
        return try link(for: file)
    }

    private static func link(for file: LibraryFile) throws -> URL {
        let raw = file.url.isEmpty ? file.rawUrl : file.url
        guard let url = URL(string: raw), !raw.isEmpty else { throw UploadServiceError.noLink }
        return url
    }
}

struct UploadToJuiceboxIntent: AppIntent {
    static var title: LocalizedStringResource = "Upload to Juicebox"
    static var description = IntentDescription(
        "Upload a file to Juicebox and return the share link."
    )
    static var openAppWhenRun: Bool = false

    @Parameter(title: "File")
    var file: IntentFile

    static var parameterSummary: some ParameterSummary {
        Summary("Upload \(\.$file) to Juicebox")
    }

    func perform() async throws -> some IntentResult & ReturnsValue<URL> & ProvidesDialog {
        let url = try await UploadService.uploadFile(file)
        return .result(value: url, dialog: "Uploaded to Juicebox")
    }
}

struct UploadTextToJuiceboxIntent: AppIntent {
    static var title: LocalizedStringResource = "Upload text to Juicebox"
    static var description = IntentDescription(
        "Upload pasted text as a .txt file and return the share link."
    )
    static var openAppWhenRun: Bool = false

    @Parameter(title: "Text")
    var text: String

    static var parameterSummary: some ParameterSummary {
        Summary("Upload \(\.$text) to Juicebox")
    }

    func perform() async throws -> some IntentResult & ReturnsValue<URL> & ProvidesDialog {
        let url = try await UploadService.uploadText(text)
        return .result(value: url, dialog: "Uploaded to Juicebox")
    }
}

struct JuiceboxShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: UploadToJuiceboxIntent(),
            phrases: [
                "Upload to \(.applicationName)",
                "Send a file with \(.applicationName)",
            ],
            shortTitle: "Upload to Juicebox",
            systemImageName: "arrow.up.doc.fill"
        )
        AppShortcut(
            intent: UploadTextToJuiceboxIntent(),
            phrases: ["Upload text with \(.applicationName)"],
            shortTitle: "Upload text to Juicebox",
            systemImageName: "text.alignleft"
        )
    }
}
