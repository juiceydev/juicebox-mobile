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
    @Published var isFetching = false
    @Published var isStaging = false
    /// The true fraction from the transfer engine; the text and Live Activity
    /// read this one.
    @Published var uploadProgress = 0.0
    /// Eased towards `uploadProgress` so the bar never jumps between updates.
    @Published var displayProgress = 0.0
    @Published var uploadBytesSent: Int64 = 0
    @Published var uploadBytesTotal: Int64 = 0
    @Published var uploadSpeed: Double = 0
    @Published var reservedUrl: String?
    @Published var reservedRawUrl: String?
    @Published var reservedDownloadUrl: String?
    @Published var sessionItems: [UploadSessionItem] = []
    @Published var lastUpload: LibraryFile?
    @Published var toast: String?
    @Published var communityHosts: [StorageHost] = []
    @Published var communityHostsLoaded = false

    private var subscriptions: [IosSubscription] = []
    private var toastTask: Task<Void, Never>?
    private var progressTicker: Timer?
    private var currentSessionId: String?

    init() {
        let bridge = JuiceboxRuntime.bridge
        self.version = JuiceboxRuntime.version
        self.bridge = bridge
        self.settings = bridge.currentSettings()
        self.files = bridge.currentFiles()
        self.connection = bridge.currentConnection()
        self.config = bridge.currentConfig()
        self.ttlOptions = bridge.ttlOptions()
        self.selectedMinutes = bridge.defaultTtlMinutes()
        self.announcement = nil

        var restored = UploadSessionStore.load()
        for index in restored.indices where restored[index].isActive {
            restored[index].status = "failed"
            restored[index].error = "Interrupted"
        }
        self.sessionItems = restored
        if ProcessInfo.processInfo.environment["JUICEBOX_DEMO_SESSION"] == "1" {
            self.sessionItems = UploadSessionStore.demoItems()
        }

        if Self.demoLibrary {
            self.files = bridge.screenshotFiles()
        }

        // Screenshot runs pin the glass setting so light/dark captures are
        // reproducible; a normal launch keeps whatever Settings stored.
        if let glass = ProcessInfo.processInfo.environment["JUICEBOX_LIQUID_GLASS"] {
            let on = glass == "1" || glass.lowercased() == "true"
            if bridge.currentSettings().liquidGlass != on {
                bridge.setLiquidGlass(value: on)
                self.settings = bridge.currentSettings()
            }
        }

        // Screenshot runs can also pin a live upload for the Dynamic Island
        // captures. The request happens from RootView once the scene is active;
        // ActivityKit refuses to start one during app launch.

        subscriptions.append(bridge.observeSettings { [weak self] value in
            self?.settings = value
        })
        subscriptions.append(bridge.observeLibrary { [weak self] value in
            guard let self, !Self.demoLibrary else { return }
            self.files = value
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

    /// Screenshot runs seed a few rows so the Files and Library screens have
    /// content without touching the network or the real library.
    private static var demoLibrary: Bool {
        ProcessInfo.processInfo.environment["JUICEBOX_DEMO_LIBRARY"] == "1"
    }

    /// Screenshot runs can pin a live upload for the Dynamic Island captures.
    static var demoUpload: Bool {
        ProcessInfo.processInfo.environment["JUICEBOX_DEMO_UPLOAD"] == "1"
    }

    /// Started from the UI so ActivityKit sees a foreground scene.
    @discardableResult
    func startDemoUpload() -> Bool {
        guard Self.demoUpload else { return true }
        let started = LiveUploadActivity.shared.start(
            filename: "launch-teaser.mp4",
            totalBytes: 48_233_472,
            server: URL(string: settings.serverUrl)?.host ?? "juicey.dev"
        )
        if started {
            LiveUploadActivity.shared.report(progress: 0.62)
            LiveUploadActivity.shared.keepFresh()
        }
        return started
    }

    // MARK: - Theme

    func palette(_ scheme: ColorScheme) -> JuicePalette {
        JuiceTheme.palette(mode: settings.themeMode, dark: scheme == .dark)
    }

    var selectedTtl: Double { Double(selectedMinutes) / 60 }

    /// The server's size allowance shrinks as retention grows; never compare
    /// a picked file against the old single global cap.
    var maxUploadBytes: Int64 {
        guard let config else { return 500 * 1024 * 1024 }
        return config.maxFileSizeBytes(ttlHours: selectedTtl)
    }

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

    private func apply(_ progress: IosUploadProgress) {
        uploadProgress = progress.fraction
        uploadBytesSent = Int64(progress.sentBytes)
        uploadBytesTotal = Int64(progress.totalBytes)
        uploadSpeed = progress.bytesPerSecond
        updateSession(currentSessionId) { item in
            item.status = "uploading"
            item.fraction = progress.fraction
            item.bytesSent = uploadBytesSent
            item.sizeBytes = uploadBytesTotal
            item.speed = progress.bytesPerSecond
        }
        LiveUploadActivity.shared.report(
            progress: progress.fraction,
            totalBytes: uploadBytesTotal
        )
    }

    private func updateSession(
        _ id: String?,
        _ change: (inout UploadSessionItem) -> Void
    ) {
        guard let id, let index = sessionItems.firstIndex(where: { $0.id == id }) else { return }
        change(&sessionItems[index])
        persistSessionItems()
    }

    private func persistSessionItems() {
        sessionItems = UploadSessionStore.prune(sessionItems)
        UploadSessionStore.save(sessionItems)
    }

    func removeSessionItem(_ id: String) {
        sessionItems.removeAll { $0.id == id }
        persistSessionItems()
    }

    func clearSessionUploads() {
        sessionItems.removeAll { !$0.isActive }
        persistSessionItems()
    }

    /// The core reports exact bytes every 100 ms; easing between those samples
    /// keeps the bar moving without lying about the percentage.
    private func startProgressTicker() {
        stopProgressTicker()
        displayProgress = 0
        let ticker = Timer.scheduledTimer(withTimeInterval: 1.0 / 30.0, repeats: true) {
            [weak self] _ in
            guard let self else { return }
            let delta = self.uploadProgress - self.displayProgress
            if abs(delta) < 0.002 {
                self.displayProgress = self.uploadProgress
            } else {
                self.displayProgress += delta * 0.28
            }
            if self.uploadProgress >= 1 { self.stopProgressTicker() }
        }
        RunLoop.main.add(ticker, forMode: .common)
        progressTicker = ticker
    }

    private func stopProgressTicker() {
        progressTicker?.invalidate()
        progressTicker = nil
    }

    func stageAndUpload(_ url: URL) {
        guard !isStaging, !isUploading else {
            flash("An upload is already running")
            return
        }
        let name = url.lastPathComponent.isEmpty ? "file" : url.lastPathComponent
        uploadProgress = 0
        lastUpload = nil
        isStaging = true
        // A photo-library video can be gigabytes; copying it on the main
        // thread froze the screen before the upload even started.
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            let scoped = url.startAccessingSecurityScopedResource()
            defer {
                if scoped { url.stopAccessingSecurityScopedResource() }
            }
            let directory = FileManager.default.temporaryDirectory
                .appendingPathComponent("juicebox-\(UUID().uuidString)", isDirectory: true)
            do {
                try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
                let destination = directory.appendingPathComponent(name)
                try FileManager.default.copyItem(at: url, to: destination)
                let mime = UTType(filenameExtension: url.pathExtension)?.preferredMIMEType
                    ?? "application/octet-stream"
                DispatchQueue.main.async {
                    guard let self else { return }
                    self.isStaging = false
                    self.startUpload(path: destination.path, filename: name, mime: mime)
                }
            } catch {
                DispatchQueue.main.async {
                    guard let self else { return }
                    self.isStaging = false
                    self.flash("Couldn't read that file")
                }
            }
        }
    }

    func upload(_ data: Data, filename: String, mime: String) {
        guard !isStaging, !isUploading else {
            flash("An upload is already running")
            return
        }
        uploadProgress = 0
        lastUpload = nil
        isStaging = true
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            let directory = FileManager.default.temporaryDirectory
                .appendingPathComponent("juicebox-\(UUID().uuidString)", isDirectory: true)
            do {
                try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
                let destination = directory.appendingPathComponent(filename)
                try data.write(to: destination)
                DispatchQueue.main.async {
                    guard let self else { return }
                    self.isStaging = false
                    self.startUpload(path: destination.path, filename: filename, mime: mime)
                }
            } catch {
                DispatchQueue.main.async {
                    guard let self else { return }
                    self.isStaging = false
                    self.flash("Couldn't stage that file")
                }
            }
        }
    }

    func saveText(_ text: String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            flash("Nothing to save")
            return
        }
        guard beginUpload(filename: "paste.txt", size: Int64(text.utf8.count)) else { return }
        bridge.saveText(
            text: text,
            ttlMinutes: selectedMinutes,
            onProgress: { [weak self] value in
                self?.apply(value)
            },
            completion: { [weak self] file, error in
                self?.finishUpload(file: file, error: error)
            },
            onReserved: { [weak self] link in
                guard let self else { return }
                self.reservedUrl = link.url
                self.reservedRawUrl = link.rawUrl
                self.reservedDownloadUrl = link.downloadUrl
                self.copy(
                    self.preferredLink(
                        preview: link.url,
                        raw: link.rawUrl,
                        download: link.downloadUrl
                    )
                )
                self.updateSession(self.currentSessionId) { item in
                    item.url = link.url
                    item.rawUrl = link.rawUrl
                    item.downloadUrl = link.downloadUrl
                    item.deleteToken = link.deleteToken
                }
            }
        )
    }

    /// Fetches a video or audio link through the server's cobalt pipeline and
    /// records the resulting file in the library, mirroring Android's Media
    /// mode. Returns false when the request never started.
    @discardableResult
    func fetchMedia(
        url: String,
        audioOnly: Bool,
        quality: String,
        container: String,
        audioFormat: String,
        betterAudio: Bool
    ) -> Bool {
        let link = url.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !link.isEmpty else {
            flash("Paste a link first")
            return false
        }
        guard config?.cobalt == true else {
            flash("This server can't fetch links")
            return false
        }
        let source = URL(string: link)?.host ?? "Link"
        guard beginUpload(filename: source, size: 0) else { return false }
        isFetching = true
        bridge.fetchMedia(
            url: link,
            audioOnly: audioOnly,
            videoQuality: quality,
            videoContainer: container,
            audioFormat: audioFormat,
            betterAudio: betterAudio,
            onProgress: { [weak self] fraction, total, _ in
                let progress = fraction.doubleValue
                let bytes = Int64(total.doubleValue)
                self?.uploadProgress = progress
                self?.uploadBytesTotal = bytes
                LiveUploadActivity.shared.report(progress: progress, totalBytes: bytes)
            },
            completion: { [weak self] file, error in
                self?.finishUpload(file: file, error: error)
            }
        )
        return true
    }

    private func startUpload(path: String, filename: String, mime: String) {
        let attributes = try? FileManager.default.attributesOfItem(atPath: path)
        let size = (attributes?[.size] as? NSNumber)?.int64Value ?? 0
        let max = maxUploadBytes
        if max > 0, size > max {
            flash("Too large. This server allows \(JuiceFormat.bytes(max)) for this retention")
            return
        }
        guard beginUpload(filename: filename, size: size) else { return }
        bridge.uploadFile(
            path: path,
            filename: filename,
            mimeType: mime,
            ttlMinutes: selectedMinutes,
            onProgress: { [weak self] value in
                self?.apply(value)
            },
            completion: { [weak self] file, error in
                self?.finishUpload(file: file, error: error)
            },
            onReserved: { [weak self] link in
                guard let self else { return }
                self.reservedUrl = link.url
                self.reservedRawUrl = link.rawUrl
                self.reservedDownloadUrl = link.downloadUrl
                self.copy(
                    self.preferredLink(
                        preview: link.url,
                        raw: link.rawUrl,
                        download: link.downloadUrl
                    )
                )
                self.updateSession(self.currentSessionId) { item in
                    item.url = link.url
                    item.rawUrl = link.rawUrl
                    item.downloadUrl = link.downloadUrl
                    item.deleteToken = link.deleteToken
                }
            }
        )
    }

    private func beginUpload(filename: String, size: Int64) -> Bool {
        if isUploading || isStaging {
            flash("An upload is already running")
            return false
        }
        isUploading = true
        uploadProgress = 0
        displayProgress = 0
        uploadBytesSent = 0
        uploadBytesTotal = size
        uploadSpeed = 0
        reservedUrl = nil
        reservedRawUrl = nil
        reservedDownloadUrl = nil
        lastUpload = nil
        let sessionId = UUID().uuidString
        currentSessionId = sessionId
        sessionItems.insert(
            UploadSessionItem(
                id: sessionId,
                name: filename,
                startedAt: Date(),
                status: "uploading",
                fraction: 0,
                bytesSent: 0,
                sizeBytes: size,
                speed: 0
            ),
            at: 0
        )
        persistSessionItems()
        startProgressTicker()
        LiveUploadActivity.shared.start(filename: filename, totalBytes: size, server: serverHost)
        return true
    }

    private func finishUpload(file: LibraryFile?, error: String?) {
        isUploading = false
        isFetching = false
        stopProgressTicker()
        let sessionId = currentSessionId
        currentSessionId = nil
        updateSession(sessionId) { item in
            if let file {
                item.status = "done"
                item.fraction = 1
                item.bytesSent = file.sizeBytes
                item.sizeBytes = file.sizeBytes
                item.libraryId = file.localId
                item.url = file.url
                item.rawUrl = file.rawUrl
                item.downloadUrl = file.downloadUrl
                item.deleteToken = file.deleteToken
                item.error = nil
            } else {
                item.status = "failed"
                item.error = error ?? "Upload failed"
            }
        }
        if let file {
            uploadProgress = 1
            displayProgress = 1
            lastUpload = file
            let shareLink = link(file, variant: settings.linkVariant)
            if settings.autoCopy {
                UIPasteboard.general.string = shareLink
            }
            LiveUploadActivity.shared.finish(link: shareLink)
            haptic()
            flash("Uploaded")
        } else {
            LiveUploadActivity.shared.fail()
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

    var libraryCount: Int { files.count }

    var storageHosts: [StorageHost] { bridge.storageHosts() }

    func loadCommunityHosts() {
        guard !communityHostsLoaded else { return }
        communityHostsLoaded = true
        bridge.communityHosts { [weak self] hosts, _ in
            guard let self else { return }
            self.communityHosts = hosts ?? []
        }
    }

    func validateHost(_ url: String) async -> HostValidation? {
        await withCheckedContinuation { (continuation: CheckedContinuation<HostValidation?, Never>) in
            bridge.validateStorageHost(url: url) { result, error in
                continuation.resume(returning: error == nil ? result : nil)
            }
        }
    }

    func saveCustomHost(_ url: String) {
        bridge.setStorageHost(url: url)
        bridge.rememberCustomHost(url: url)
    }

    func exportLibrary(includeSession: Bool) async -> String? {
        await withCheckedContinuation { (continuation: CheckedContinuation<String?, Never>) in
            bridge.exportLibrary(includeSession: includeSession) { [weak self] raw, error in
                self?.report(error)
                continuation.resume(returning: error == nil ? raw : nil)
            }
        }
    }

    func importLibrary(_ raw: String) async -> Int? {
        await withCheckedContinuation { (continuation: CheckedContinuation<Int?, Never>) in
            bridge.importLibrary(raw: raw) { [weak self] count, error in
                guard let self else {
                    continuation.resume(returning: nil)
                    return
                }
                self.report(error)
                if error == nil {
                    self.flash("Restored \(count?.intValue ?? 0) files")
                }
                continuation.resume(returning: error == nil ? count?.intValue : nil)
            }
        }
    }

    func clearLibrary() async -> Bool {
        await withCheckedContinuation { (continuation: CheckedContinuation<Bool, Never>) in
            bridge.clearLibrary { [weak self] error in
                guard let self else {
                    continuation.resume(returning: false)
                    return
                }
                self.report(error)
                if error == nil { self.flash("Local library cleared") }
                continuation.resume(returning: error == nil)
            }
        }
    }


    func link(_ file: LibraryFile, variant: String) -> String {
        switch variant {
        case "raw": return file.rawUrl.isEmpty ? swapPrefix(file.url, "f") : file.rawUrl
        case "download": return file.downloadUrl.isEmpty ? swapPrefix(file.url, "d") : file.downloadUrl
        default: return file.url.isEmpty ? file.rawUrl : file.url
        }
    }

    private func preferredLink(preview: String, raw: String, download: String) -> String {
        switch settings.linkVariant {
        case "raw": return raw.isEmpty ? preview : raw
        case "download": return download.isEmpty ? preview : download
        default: return preview.isEmpty ? raw : preview
        }
    }

    func link(_ item: UploadSessionItem, variant: String) -> String {
        let url = item.url ?? ""
        switch variant {
        case "raw":
            if let raw = item.rawUrl, !raw.isEmpty { return raw }
            return swapPrefix(url, "f")
        case "download":
            if let download = item.downloadUrl, !download.isEmpty { return download }
            return swapPrefix(url, "d")
        default:
            return url.isEmpty ? (item.rawUrl ?? "") : url
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
    func setLiquidGlass(_ value: Bool) { bridge.setLiquidGlass(value: value) }
    func setLockUploads(_ value: Bool) { bridge.setLockUploads(value: value) }
    func setUploadPassword(_ value: String) { bridge.setUploadPassword(value: value) }
    func setLinkVariant(_ value: String) { bridge.setLinkVariant(value: value) }
    func setShareLinkMode(_ value: String) { bridge.setShareLinkMode(value: value) }
    func setStorageHost(_ value: String?) { bridge.setStorageHost(url: value) }
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
