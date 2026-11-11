import Combine
import Foundation
import JuiceboxCore
import SwiftUI
import UIKit
import UniformTypeIdentifiers

final class AppModel: ObservableObject {

    let bridge: IosBridge
    let version: String

    @Published var settings: AppSettings
    @Published var files: [LibraryFile]
    @Published var connection: ConnectionState
    @Published var config: ServerConfig?
    @Published var announcement: SiteAnnouncement?
    @Published var ttlOptions: [IosTtlOption]
    @Published var selectedMinutes: Int32
    @Published var isUploading = false
    @Published var uploadProgress = 0.0
    @Published var lastUpload: LibraryFile?
    @Published var toast: String?

    private var subscriptions: [IosSubscription] = []
    private var toastTask: Task<Void, Never>?

    init() {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0"
        let bridge = IosBridge(version: version)
        self.version = version
        self.bridge = bridge
        self.settings = bridge.currentSettings()
        self.files = bridge.currentFiles()
        self.connection = bridge.currentConnection()
        self.config = bridge.currentConfig()
        self.ttlOptions = bridge.ttlOptions()
        self.selectedMinutes = bridge.defaultTtlMinutes()
        self.announcement = nil

        subscriptions.append(bridge.observeSettings { [weak self] value in
            self?.settings = value
        })
        subscriptions.append(bridge.observeLibrary { [weak self] value in
            self?.files = value
        })
        subscriptions.append(bridge.observeConnection { [weak self] value in
            self?.connection = value
        })
        subscriptions.append(bridge.observeConfig { [weak self] value in
            guard let self else { return }
            self.config = value
            self.ttlOptions = self.bridge.ttlOptions()
            if !self.ttlOptions.contains(where: { $0.minutes == self.selectedMinutes }) {
                self.selectedMinutes = self.bridge.defaultTtlMinutes()
            }
        })
        subscriptions.append(bridge.observeAnnouncement { [weak self] value in
            self?.announcement = value
        })

        bridge.initialise { [weak self] error in
            guard let self else { return }
            self.report(error)
            self.selectedMinutes = self.bridge.defaultTtlMinutes()
        }
    }

    deinit {
        subscriptions.forEach { $0.cancel() }
        toastTask?.cancel()
    }

    // MARK: - Theme

    func palette(_ scheme: ColorScheme) -> JuicePalette {
        JuiceTheme.palette(mode: settings.themeMode, dark: scheme == .dark)
    }

    var selectedTtl: Double { Double(selectedMinutes) / 60 }

    var serverHost: String {
        URL(string: settings.serverUrl)?.host ?? settings.serverUrl
    }

    var statusText: String {
        if connection.connecting { return "Connecting" }
        if let error = connection.error, !error.isEmpty { return error }
        if connection.connected {
            if let version = config?.version, !version.isEmpty { return "v\(version)" }
            return "Connected"
        }
        return "Offline"
    }

    // MARK: - Session

    func connect() async {
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            bridge.connect { [weak self] _, error in
                self?.report(error)
                continuation.resume()
            }
        }
    }

    func refreshLibrary() async {
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            bridge.refreshLibrary { [weak self] _, error in
                self?.report(error)
                continuation.resume()
            }
        }
    }

    func applyServer(_ input: String) async -> Bool {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return false }
        return await withCheckedContinuation { (continuation: CheckedContinuation<Bool, Never>) in
            bridge.setServer(input: trimmed) { [weak self] config, error in
                guard let self else {
                    continuation.resume(returning: false)
                    return
                }
                self.report(error)
                if config != nil {
                    self.ttlOptions = self.bridge.ttlOptions()
                    self.selectedMinutes = self.bridge.defaultTtlMinutes()
                    self.flash("Connected to \(self.serverHost)")
                }
                continuation.resume(returning: error == nil)
            }
        }
    }

    func forgetDevice() async -> Bool {
        await withCheckedContinuation { (continuation: CheckedContinuation<Bool, Never>) in
            bridge.forgetSession { [weak self] error in
                self?.report(error)
                continuation.resume(returning: error == nil)
            }
        }
    }

    // MARK: - Uploads

    func stageAndUpload(_ url: URL) {
        let scoped = url.startAccessingSecurityScopedResource()
        defer {
            if scoped { url.stopAccessingSecurityScopedResource() }
        }
        let name = url.lastPathComponent.isEmpty ? "file" : url.lastPathComponent
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("juicebox-\(UUID().uuidString)", isDirectory: true)
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            let destination = directory.appendingPathComponent(name)
            try FileManager.default.copyItem(at: url, to: destination)
            let mime = UTType(filenameExtension: url.pathExtension)?.preferredMIMEType
                ?? "application/octet-stream"
            startUpload(path: destination.path, filename: name, mime: mime)
        } catch {
            flash("Couldn't read that file")
        }
    }

    func upload(_ data: Data, filename: String, mime: String) {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("juicebox-\(UUID().uuidString)", isDirectory: true)
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            let destination = directory.appendingPathComponent(filename)
            try data.write(to: destination)
            startUpload(path: destination.path, filename: filename, mime: mime)
        } catch {
            flash("Couldn't stage that file")
        }
    }

    func saveText(_ text: String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            flash("Nothing to save")
            return
        }
        guard beginUpload() else { return }
        bridge.saveText(
            text: text,
            ttlMinutes: selectedMinutes,
            onProgress: { [weak self] value in
                self?.uploadProgress = value.doubleValue
            },
            completion: { [weak self] file, error in
                self?.finishUpload(file: file, error: error)
            }
        )
    }

    private func startUpload(path: String, filename: String, mime: String) {
        let attributes = try? FileManager.default.attributesOfItem(atPath: path)
        let size = (attributes?[.size] as? NSNumber)?.int64Value ?? 0
        if let max = config?.maxFileSizeBytes, max > 0, size > max {
            flash("Too large — this server allows \(JuiceFormat.bytes(max))")
            return
        }
        guard beginUpload() else { return }
        bridge.uploadFile(
            path: path,
            filename: filename,
            mimeType: mime,
            ttlMinutes: selectedMinutes,
            onProgress: { [weak self] value in
                self?.uploadProgress = value.doubleValue
            },
            completion: { [weak self] file, error in
                self?.finishUpload(file: file, error: error)
            }
        )
    }

    private func beginUpload() -> Bool {
        if isUploading {
            flash("An upload is already running")
            return false
        }
        isUploading = true
        uploadProgress = 0
        lastUpload = nil
        return true
    }

    private func finishUpload(file: LibraryFile?, error: String?) {
        isUploading = false
        if let file {
            uploadProgress = 1
            lastUpload = file
            if settings.autoCopy {
                UIPasteboard.general.string = link(file, variant: settings.linkVariant)
            }
            haptic()
            flash("Uploaded")
        }
        report(error)
    }

    // MARK: - Library

    func delete(_ file: LibraryFile) async -> Bool {
        await withCheckedContinuation { (continuation: CheckedContinuation<Bool, Never>) in
            bridge.deleteFile(localId: file.localId) { [weak self] error in
                guard let self else {
                    continuation.resume(returning: false)
                    return
                }
                self.report(error)
                if error == nil { self.flash("Deleted") }
                continuation.resume(returning: error == nil)
            }
        }
    }

    func refresh(_ file: LibraryFile) async -> LibraryFile? {
        await withCheckedContinuation { (continuation: CheckedContinuation<LibraryFile?, Never>) in
            bridge.refreshFile(localId: file.localId) { [weak self] updated, error in
                self?.report(error)
                continuation.resume(returning: updated)
            }
        }
    }

    func isLocked(_ file: LibraryFile) -> Bool { bridge.isLocked(file: file) }

    func link(_ file: LibraryFile, variant: String) -> String {
        switch variant {
        case "raw": return file.rawUrl.isEmpty ? swapPrefix(file.url, "f") : file.rawUrl
        case "download": return file.downloadUrl.isEmpty ? swapPrefix(file.url, "d") : file.downloadUrl
        default: return file.url.isEmpty ? file.rawUrl : file.url
        }
    }

    private func swapPrefix(_ value: String, _ prefix: String) -> String {
        guard let range = value.range(of: "/v/") else { return value }
        return value.replacingCharacters(in: range, with: "/\(prefix)/")
    }

    func copy(_ text: String) {
        UIPasteboard.general.string = text
        flash("Copied")
    }

    // MARK: - Settings

    func setTheme(_ mode: String) { bridge.setThemeMode(value: mode) }
    func setQuickLink(_ value: Bool) { bridge.setQuickLink(value: value) }
    func setAutoCopy(_ value: Bool) { bridge.setAutoCopy(value: value) }
    func setHaptics(_ value: Bool) { bridge.setHaptics(value: value) }
    func setLockUploads(_ value: Bool) { bridge.setLockUploads(value: value) }
    func setUploadPassword(_ value: String) { bridge.setUploadPassword(value: value) }
    func setLinkVariant(_ value: String) { bridge.setLinkVariant(value: value) }
    func setShareLinkMode(_ value: String) { bridge.setShareLinkMode(value: value) }
    func setLastTtl(_ minutes: Int32) { bridge.setLastTtlMinutes(minutes: minutes) }

    func flash(_ message: String) {
        toast = message
        toastTask?.cancel()
        toastTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 2_400_000_000)
            if Task.isCancelled { return }
            self?.toast = nil
        }
    }

    func haptic() {
        guard settings.haptics else { return }
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    private func report(_ message: String?) {
        guard let message, !message.isEmpty else { return }
        flash(message)
    }
}
