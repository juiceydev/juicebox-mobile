import JuiceboxCore
import PhotosUI
import SwiftUI
import UIKit
import UniformTypeIdentifiers

struct UploadView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.colorScheme) private var scheme
    @Environment(\.juiceGlass) private var glass

    @State private var mode: UploadMode = .files
    @State private var showFileImporter = false
    @State private var showCamera = false
    @State private var photoItem: PhotosPickerItem?
    @State private var text = ""
    @State private var link = ""
    @State private var audioOnly = false
    @State private var betterAudio = true
    @State private var audioFormat = "best"
    @State private var quality = "max"
    @State private var container = "auto"
    @FocusState private var textFocused: Bool
    @FocusState private var linkFocused: Bool

    enum UploadMode: String, CaseIterable, Identifiable {
        case files = "Files"
        case media = "Media"
        case text = "Text"

        var id: String { rawValue }
    }

    private struct FetchOption<Value: Hashable>: Identifiable {
        let label: String
        let value: Value
        var id: Value { value }
    }

    /// Screenshot runs can start on a specific mode with
    /// SIMCTL_CHILD_JUICEBOX_UPLOAD_MODE.
    init() {
        if let raw = ProcessInfo.processInfo.environment["JUICEBOX_UPLOAD_MODE"],
           let start = UploadMode(rawValue: raw.capitalized) {
            _mode = State(initialValue: start)
        }
    }

    var body: some View {
        let palette = model.palette(scheme)
        GeometryReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    Masthead()

                    if !model.connection.connected && !model.connection.connecting {
                        offlineNotice(palette)
                            .transition(.move(edge: .top).combined(with: .opacity))
                    }

                    if let announcement = model.announcement,
                       let message = announcement.message,
                       !message.isEmpty {
                        AnnouncementBar(announcement: announcement, palette: palette)
                            .padding(.horizontal, 16)
                            .transition(.move(edge: .top).combined(with: .opacity))
                    }

                    modePicker(palette)
                    retention(palette)

                    if mode == .files {
                        Spacer(minLength: 0)
                    }

                    if mode == .text {
                        textEditor(palette)
                            .transition(.opacity.combined(with: .move(edge: .top)))
                    } else if mode == .media {
                        fetchForm(palette)
                            .transition(.opacity.combined(with: .move(edge: .top)))
                    } else {
                        dropZone(palette)
                            .transition(.opacity.combined(with: .move(edge: .top)))
                    }

                    if mode == .files {
                        actionRow(palette)
                            .transition(.opacity)
                    }

                    if mode == .media {
                        fetchRow(palette)
                            .transition(.opacity)
                    }

                    if mode == .text {
                        saveRow(palette)
                            .transition(.opacity)
                    }

                    if model.isUploading || model.isStaging {
                        progressCard(palette)
                            .transition(
                                .asymmetric(
                                    insertion: .scale(scale: 0.96, anchor: .top).combined(with: .opacity),
                                    removal: .opacity
                                )
                            )
                    }

                    if let file = model.lastUpload {
                        resultCard(file, palette)
                            .transition(
                                .asymmetric(
                                    insertion: .scale(scale: 0.96, anchor: .top).combined(with: .opacity),
                                    removal: .opacity
                                )
                            )
                            .id(file.localId)
                    }

                    if model.settings.lockUploads {
                        lockNote(palette)
                    }

                    if mode == .files {
                        Spacer(minLength: 0)
                    }
                }
                .padding(.bottom, mode == .media ? 76 : 28)
                .frame(minHeight: proxy.size.height, alignment: .top)
                .animation(.easeInOut(duration: 0.24), value: mode)
                .animation(.snappy(duration: 0.3), value: model.connection.connected)
                .animation(.spring(duration: 0.35), value: model.isUploading || model.isStaging)
                .animation(.spring(duration: 0.35), value: model.lastUpload?.localId)
                .animation(.easeOut(duration: 0.24), value: model.announcement?.message)
            }
            .scrollDismissesKeyboard(.interactively)
            .background(palette.background.ignoresSafeArea())
            .overlay(alignment: .bottomTrailing) {
                pasteAction(palette)
            }
            .fileImporter(
                isPresented: $showFileImporter,
                allowedContentTypes: [.item],
                allowsMultipleSelection: false
            ) { result in
                switch result {
                case .success(let urls):
                    if let url = urls.first { model.stageAndUpload(url) }
                case .failure:
                    model.flash("Couldn't open that file")
                }
            }
            .onChange(of: photoItem) { _, item in
                loadPhoto(item)
            }
            .onChange(of: mode) { _, value in
                textFocused = value == .text
                linkFocused = value == .media
            }
            .onChange(of: model.config?.cobalt) { _, cobalt in
                guard model.config != nil, cobalt != true, mode == .media else { return }
                mode = .files
            }
            .fullScreenCover(isPresented: $showCamera) {
                CameraPicker { data in
                    let stamp = Int(Date().timeIntervalSince1970)
                    model.upload(data, filename: "photo-\(stamp).jpg", mime: "image/jpeg")
                }
            }
        }
    }

    // MARK: - Sections

    private func offlineNotice(_ palette: JuicePalette) -> some View {
        HStack(spacing: 12) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 18, weight: .regular))
                .foregroundStyle(palette.error)
            VStack(alignment: .leading, spacing: 2) {
                Text("Server unreachable")
                    .font(JuiceFont.body(14, weight: .semibold))
                    .foregroundStyle(palette.onSurface)
                Text(model.connection.error ?? "Check the server in Settings")
                    .font(JuiceFont.body(12))
                    .foregroundStyle(palette.onSurfaceVariant)
                    .lineLimit(2)
            }
            Spacer(minLength: 8)
            Button {
                Task { await model.connect() }
            } label: {
                Text("Retry")
            }
            .buttonStyle(.bordered)
            .controlSize(.small)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(
            palette.surfaceContainerLow,
            in: RoundedRectangle(cornerRadius: 16, style: .continuous)
        )
        .padding(.horizontal, 20)
    }

    private var canFetch: Bool { model.config?.cobalt == true }

    private var availableModes: [UploadMode] {
        UploadMode.allCases.filter { $0 != .media || canFetch || mode == .media }
    }

    private func modePicker(_ palette: JuicePalette) -> some View {
        Picker("Upload mode", selection: $mode) {
            ForEach(availableModes) { item in
                Text(item.rawValue).tag(item)
            }
        }
        .pickerStyle(.segmented)
        .tint(palette.primary)
        .padding(.horizontal, 20)
    }

    private var ttlIndex: Int {
        let options = model.ttlOptions
        if let exact = options.firstIndex(where: { $0.minutes == model.selectedMinutes }) {
            return exact
        }
        return options.indices.min(by: {
            abs(options[$0].minutes - model.selectedMinutes) <
            abs(options[$1].minutes - model.selectedMinutes)
        }) ?? 0
    }

    @ViewBuilder
    private func retention(_ palette: JuicePalette) -> some View {
        let options = model.ttlOptions
        if !options.isEmpty {
            VStack(alignment: .leading, spacing: 4) {
                HStack(alignment: .firstTextBaseline) {
                    Text("Retention")
                        .font(JuiceFont.body(14, weight: .medium))
                        .foregroundStyle(palette.onSurfaceVariant)
                    Spacer(minLength: 8)
                    Text(TtlFormat.label(minutes: options[ttlIndex].minutes))
                        .font(JuiceFont.body(16, weight: .medium))
                        .foregroundStyle(palette.primary)
                }
                Slider(
                    value: Binding(
                        get: { Double(ttlIndex) },
                        set: { raw in
                            let next = Int(raw.rounded())
                            guard next != ttlIndex, options.indices.contains(next) else { return }
                            model.selectedMinutes = options[next].minutes
                            model.setLastTtl(options[next].minutes)
                        }
                    ),
                    in: 0...Double(max(1, options.count - 1))
                ) {
                    Text("Retention")
                } minimumValueLabel: {
                    Text(options.first?.label ?? "")
                        .font(JuiceFont.body(10))
                        .foregroundStyle(palette.onSurfaceVariant)
                } maximumValueLabel: {
                    Text(options.last?.label ?? "")
                        .font(JuiceFont.body(10))
                        .foregroundStyle(palette.onSurfaceVariant)
                }
                .labelsHidden()
                .tint(palette.primary)
                .accessibilityValue(TtlFormat.label(minutes: options[ttlIndex].minutes))
                .accessibilityAdjustableAction { direction in
                    let delta: Int
                    switch direction {
                    case .increment: delta = 1
                    case .decrement: delta = -1
                    @unknown default: return
                    }
                    let next = min(max(ttlIndex + delta, 0), options.count - 1)
                    guard next != ttlIndex else { return }
                    model.selectedMinutes = options[next].minutes
                    model.setLastTtl(options[next].minutes)
                }
                .sensoryFeedback(
                    .selection,
                    trigger: model.selectedMinutes,
                    condition: { _, _ in model.settings.haptics }
                )
            }
            .padding(.horizontal, 20)
        }
    }

    @ViewBuilder
    private func dropZone(_ palette: JuicePalette) -> some View {
        Button {
            showFileImporter = true
        } label: {
            dropZoneLabel(palette, icon: "arrow.up.to.line", title: "Drop files here")
        }
        .buttonStyle(.plain)
    }

    private func dropZoneLabel(_ palette: JuicePalette, icon: String, title: String) -> some View {
        ContentUnavailableView {
            Label(title, systemImage: icon)
        } description: {
            Text("Tap to browse")
        }
        .frame(maxWidth: .infinity)
        .frame(height: 210)
        .background { dropZoneSurface(palette) }
        .overlay(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .strokeBorder(
                    palette.primary.opacity(0.45),
                    style: StrokeStyle(lineWidth: 1.2, dash: [7, 6])
                )
        )
        .padding(.horizontal, 20)
        .contentShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
    }

    /// The drop zone is a card, so the Liquid Glass setting swaps its solid
    /// surface for the system glass material.
    @ViewBuilder
    private func dropZoneSurface(_ palette: JuicePalette) -> some View {
        ZStack {
            if glass, #available(iOS 26.0, *) {
                Color.clear
                    .glassEffect(
                        .regular.tint(palette.primary.opacity(0.16)),
                        in: RoundedRectangle(cornerRadius: 24, style: .continuous)
                    )
            } else {
                palette.surfaceContainerLow
            }
            DropZoneStripes(color: palette.primary.opacity(0.035))
        }
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
    }

    private func fetchForm(_ palette: JuicePalette) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            GroupBox {
                TextField(
                    "Link",
                    text: $link,
                    prompt: Text("YouTube, TikTok, X, SoundCloud…"),
                    axis: .vertical
                )
                    .focused($linkFocused)
                    .font(JuiceFont.body(16))
                    .foregroundStyle(palette.onSurface)
                    .lineLimit(2...5)
                    .keyboardType(.URL)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .frame(maxWidth: .infinity, minHeight: 56, alignment: .topLeading)
            } label: {
                Text("Link")
                    .font(JuiceFont.body(13, weight: .medium))
                    .foregroundStyle(palette.onSurfaceVariant)
            }

            GroupBox {
                VStack(spacing: 0) {
                    Toggle("Audio only", isOn: $audioOnly)
                        .font(JuiceFont.body(15))
                        .foregroundStyle(palette.onSurface)
                        .tint(palette.primary)
                        .padding(.vertical, 12)

                    Divider()
                        .overlay(palette.outline.opacity(0.4))

                    if audioOnly {
                        optionRow(
                            "Better audio",
                            selection: $betterAudio,
                            options: [
                                FetchOption(label: "Enhanced", value: true),
                                FetchOption(label: "Standard", value: false),
                            ],
                            palette: palette
                        )
                        Divider()
                            .overlay(palette.outline.opacity(0.4))
                        optionRow(
                            "Audio format",
                            selection: $audioFormat,
                            options: [
                                FetchOption(label: "Best", value: "best"),
                                FetchOption(label: "MP3", value: "mp3"),
                                FetchOption(label: "Opus", value: "opus"),
                                FetchOption(label: "OGG", value: "ogg"),
                                FetchOption(label: "WAV", value: "wav"),
                                FetchOption(label: "FLAC", value: "flac"),
                            ],
                            palette: palette
                        )
                    } else {
                        optionRow(
                            "Quality",
                            selection: $quality,
                            options: [
                                FetchOption(label: "Max", value: "max"),
                                FetchOption(label: "2160p", value: "2160"),
                                FetchOption(label: "1440p", value: "1440"),
                                FetchOption(label: "1080p", value: "1080"),
                                FetchOption(label: "720p", value: "720"),
                                FetchOption(label: "480p", value: "480"),
                                FetchOption(label: "360p", value: "360"),
                            ],
                            palette: palette
                        )
                        Divider()
                            .overlay(palette.outline.opacity(0.4))
                        optionRow(
                            "Container",
                            selection: $container,
                            options: [
                                FetchOption(label: "Auto", value: "auto"),
                                FetchOption(label: "MP4", value: "mp4"),
                                FetchOption(label: "WebM", value: "webm"),
                                FetchOption(label: "MKV", value: "mkv"),
                            ],
                            palette: palette
                        )
                    }
                }
            }
        }
        .padding(.horizontal, 20)
    }

    private func optionRow<Value: Hashable>(
        _ title: String,
        selection: Binding<Value>,
        options: [FetchOption<Value>],
        palette: JuicePalette
    ) -> some View {
        HStack {
            Text(title)
                .font(JuiceFont.body(15))
                .foregroundStyle(palette.onSurface)
            Spacer(minLength: 8)
            Picker(title, selection: selection) {
                ForEach(options) { option in
                    Text(option.label).tag(option.value)
                }
            }
            .pickerStyle(.menu)
            .tint(palette.primary)
        }
        .padding(.vertical, 8)
    }

    private func fetchRow(_ palette: JuicePalette) -> some View {
        Button {
            let started = model.fetchMedia(
                url: link,
                audioOnly: audioOnly,
                quality: quality,
                container: container,
                audioFormat: audioFormat,
                betterAudio: betterAudio
            )
            if started {
                link = ""
                linkFocused = false
            }
        } label: {
            Label("Fetch", systemImage: "arrow.down.circle")
                .font(JuiceFont.body(16, weight: .semibold))
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.borderedProminent)
        .controlSize(.large)
        .tint(palette.primary)
        .disabled(
            model.isUploading ||
            model.isStaging ||
            link.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        )
        .padding(.horizontal, 20)
    }

    /// A one-tap shortcut for the usual Media flow: read the link the user
    /// copied, start the fetch, and leave the field empty for the next one.
    private func pasteAction(_ palette: JuicePalette) -> some View {
        Group {
            if mode == .media {
                Button {
                    pasteAndFetch()
                } label: {
                    Image(systemName: "doc.on.clipboard")
                        .font(.system(size: 18, weight: .semibold))
                        .frame(width: 24, height: 24)
                }
                .buttonStyle(.bordered)
                .buttonBorderShape(.circle)
                .controlSize(.large)
                .tint(palette.primary)
                .shadow(color: .black.opacity(0.16), radius: 12, y: 5)
                .disabled(model.isUploading || model.isStaging)
                .accessibilityLabel("Paste link and fetch")
                .transition(.scale(scale: 0.9).combined(with: .opacity))
            }
        }
        .padding(.trailing, 20)
        .padding(.bottom, 18)
        .animation(.easeInOut(duration: 0.2), value: mode)
    }

    private func pasteAndFetch() {
        guard let pasted = clipboardLink() else {
            let pasteboard = UIPasteboard.general
            model.flash(
                pasteboard.hasStrings || pasteboard.hasURLs
                    ? "Clipboard has no link"
                    : "Clipboard is empty"
            )
            return
        }
        link = pasted
        let started = model.fetchMedia(
            url: pasted,
            audioOnly: audioOnly,
            quality: quality,
            container: container,
            audioFormat: audioFormat,
            betterAudio: betterAudio
        )
        if started {
            link = ""
            linkFocused = false
        }
    }

    /// Share sheets usually wrap the link in a title or a sentence, so take
    /// the first http(s) URL out of whatever the clipboard holds.
    private func clipboardLink() -> String? {
        let pasteboard = UIPasteboard.general
        if let url = pasteboard.url,
           let scheme = url.scheme?.lowercased(),
           scheme == "http" || scheme == "https",
           url.host != nil {
            return url.absoluteString
        }
        guard pasteboard.hasStrings, let raw = pasteboard.string else { return nil }
        let pattern = #"https?://[^\s<>"']+"#
        guard let range = raw.range(of: pattern, options: [.regularExpression, .caseInsensitive])
        else { return nil }
        let trimmed = String(raw[range])
            .trimmingCharacters(in: CharacterSet(charactersIn: ".,;:!?)]}\"'"))
        guard let url = URL(string: trimmed), url.host != nil else { return nil }
        return trimmed
    }

    private func actionRow(_ palette: JuicePalette) -> some View {
        HStack(spacing: 8) {
            Button {
                showFileImporter = true
            } label: {
                Label("Files", systemImage: "folder")
                    .font(JuiceFont.body(14, weight: .semibold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                    .frame(maxWidth: .infinity)
            }
            PhotosPicker(selection: $photoItem, matching: .images, photoLibrary: .shared()) {
                Label("Photos", systemImage: "photo.on.rectangle")
                    .font(JuiceFont.body(14, weight: .semibold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                    .frame(maxWidth: .infinity)
            }
            Button {
                openCamera()
            } label: {
                Label("Camera", systemImage: "camera")
                    .font(JuiceFont.body(14, weight: .semibold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                    .frame(maxWidth: .infinity)
            }
        }
        .buttonStyle(.bordered)
        .controlSize(.regular)
        .tint(palette.primary)
        .padding(.horizontal, 20)
    }

    private func openCamera() {
        guard UIImagePickerController.isSourceTypeAvailable(.camera) else {
            model.flash("No camera on this device")
            return
        }
        showCamera = true
    }

    private func textEditor(_ palette: JuicePalette) -> some View {
        GroupBox {
            ZStack(alignment: .topLeading) {
                if text.isEmpty {
                    Text("Paste text or a link")
                        .font(JuiceFont.body(16))
                        .foregroundStyle(palette.onSurfaceVariant.opacity(0.75))
                        .padding(.horizontal, 5)
                        .padding(.top, 8)
                        .allowsHitTesting(false)
                }
                TextEditor(text: $text)
                    .focused($textFocused)
                    .font(JuiceFont.body(16))
                    .foregroundStyle(palette.onSurface)
                    .scrollContentBackground(.hidden)
            }
            .frame(minHeight: 220)
        }
        .padding(.horizontal, 20)
    }

    private func saveRow(_ palette: JuicePalette) -> some View {
        HStack {
            Text("\(text.count) characters")
                .font(JuiceFont.mono(12))
                .foregroundStyle(palette.onSurfaceVariant)
            Spacer()
            Button {
                model.saveText(text)
                text = ""
                textFocused = false
            } label: {
                Label("Save text", systemImage: "checkmark")
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .tint(palette.primary)
            .disabled(
                model.isUploading ||
                model.isStaging ||
                text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            )
        }
        .padding(.horizontal, 20)
    }

    private func progressCard(_ palette: JuicePalette) -> some View {
        GroupBox {
            VStack(alignment: .leading, spacing: 10) {
                if model.uploadProgress > 0 {
                    ProgressView(value: model.uploadProgress)
                        .tint(palette.primary)
                        .animation(.linear(duration: 0.2), value: model.uploadProgress)
                } else {
                    // Nothing has moved yet (reservation, cobalt probing), so
                    // a spinner is honest where 0% just looks stuck.
                    ProgressView()
                        .tint(palette.primary)
                }
                Text(progressLabel)
                    .font(JuiceFont.mono(13, weight: .bold))
                    .foregroundStyle(palette.primary)
            }
            .padding(.top, 4)
        } label: {
            Label(
                model.isFetching ? "Fetching" : (model.isStaging ? "Preparing" : "Uploading"),
                systemImage: model.isFetching ? "arrow.down.circle" : "arrow.up.circle"
            )
                .font(JuiceFont.body(14, weight: .semibold))
                .foregroundStyle(palette.onSurface)
        }
        .padding(.horizontal, 20)
    }

    private var progressLabel: String {
        if model.isStaging { return "Reading file" }
        let progress = model.uploadProgress
        if progress <= 0 {
            return model.isFetching ? "Preparing" : "Starting"
        }
        let percent = progress * 100
        if percent < 10 {
            let tenths = Int(percent * 10)
            if tenths <= 0 { return "<0.1%" }
            return "\(tenths / 10).\(tenths % 10)%"
        }
        return "\(Int(percent))%"
    }

    private func resultCard(_ file: LibraryFile, _ palette: JuicePalette) -> some View {
        let link = model.link(file, variant: model.settings.linkVariant)
        return GroupBox {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 10) {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(palette.success)
                    Text(file.filename.isEmpty ? "Uploaded" : file.filename)
                        .font(JuiceFont.body(15, weight: .semibold))
                        .foregroundStyle(palette.onSurface)
                        .lineLimit(1)
                    Spacer()
                    Text(JuiceFormat.bytes(file.sizeBytes))
                        .font(JuiceFont.mono(12))
                        .foregroundStyle(palette.onSurfaceVariant)
                }
                Text(link)
                    .font(JuiceFont.mono(12))
                    .foregroundStyle(palette.primary)
                    .lineLimit(1)
                    .truncationMode(.middle)

                HStack(spacing: 8) {
                    Button {
                        model.copy(link)
                    } label: {
                        Label("Copy", systemImage: "doc.on.doc")
                            .frame(maxWidth: .infinity)
                    }
                    if let url = URL(string: link) {
                        ShareLink(item: url) {
                            Label("Share", systemImage: "square.and.arrow.up")
                                .frame(maxWidth: .infinity)
                        }
                        Link(destination: url) {
                            Label("Open", systemImage: "arrow.up.right")
                                .frame(maxWidth: .infinity)
                        }
                    }
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
            }
            .padding(.top, 4)
        } label: {
            Label("Uploaded", systemImage: "checkmark.circle")
                .font(JuiceFont.body(14, weight: .semibold))
                .foregroundStyle(palette.onSurface)
        }
        .padding(.horizontal, 20)
    }

    private func lockNote(_ palette: JuicePalette) -> some View {
        Label(
            "Uploads are password-protected from this device",
            systemImage: "lock.fill"
        )
        .font(JuiceFont.body(12))
        .foregroundStyle(palette.onSurfaceVariant)
        .padding(.horizontal, 20)
    }

    private func loadPhoto(_ item: PhotosPickerItem?) {
        guard let item else { return }
        Task {
            do {
                guard let data = try await item.loadTransferable(type: Data.self) else { return }
                let type = item.supportedContentTypes.first
                let ext = type?.preferredFilenameExtension ?? "jpg"
                let mime = type?.preferredMIMEType ?? "image/jpeg"
                let name = "photo-\(Int(Date().timeIntervalSince1970)).\(ext)"
                await MainActor.run {
                    model.upload(data, filename: name, mime: mime)
                }
            } catch {
                await MainActor.run {
                    model.flash("Couldn't load that photo")
                }
            }
        }
        photoItem = nil
    }
}

/// The site's -45 degree stripes, drifting slowly behind the drop zone.
private struct DropZoneStripes: View {
    let color: Color

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1.0 / 30.0)) { timeline in
            Canvas { context, size in
                let period: CGFloat = 15
                let band = period * 0.42
                let span = (size.width + size.height) * 1.6
                let cycle = timeline.date.timeIntervalSinceReferenceDate
                    .truncatingRemainder(dividingBy: 4.2)
                let shift = CGFloat(cycle / 4.2) * period
                context.translateBy(x: size.width / 2, y: size.height / 2)
                context.rotate(by: .degrees(-45))
                context.translateBy(x: -size.width / 2, y: -size.height / 2)
                var x = -span + shift
                while x < span {
                    context.fill(
                        Path(CGRect(x: x, y: -span, width: band, height: span * 2)),
                        with: .color(color)
                    )
                    x += period
                }
            }
        }
        .clipped()
        .allowsHitTesting(false)
    }
}

/// The Android app's Camera picker: the system camera, handed back as JPEG.
struct CameraPicker: UIViewControllerRepresentable {
    let onCapture: (Data) -> Void

    @Environment(\.dismiss) private var dismiss

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ controller: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        private let parent: CameraPicker

        init(_ parent: CameraPicker) {
            self.parent = parent
        }

        func imagePickerController(
            _ picker: UIImagePickerController,
            didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
        ) {
            if let image = info[.originalImage] as? UIImage,
               let data = image.jpegData(compressionQuality: 0.9) {
                parent.onCapture(data)
            }
            parent.dismiss()
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.dismiss()
        }
    }
}
