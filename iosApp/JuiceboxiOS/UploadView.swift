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
    @FocusState private var textFocused: Bool

    enum UploadMode: String, CaseIterable, Identifiable {
        case files = "Files"
        case media = "Media"
        case text = "Text"

        var id: String { rawValue }
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

                if mode == .text {
                    textEditor(palette)
                        .transition(.opacity.combined(with: .move(edge: .top)))
                } else {
                    dropZone(palette)
                        .transition(.opacity.combined(with: .move(edge: .top)))
                }

                if mode == .files {
                    actionRow(palette)
                        .transition(.opacity)
                }

                if mode == .text {
                    saveRow(palette)
                        .transition(.opacity)
                }

                if model.isUploading {
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
            }
            .padding(.bottom, 28)
            .animation(.easeInOut(duration: 0.24), value: mode)
            .animation(.snappy(duration: 0.3), value: model.connection.connected)
            .animation(.spring(duration: 0.35), value: model.isUploading)
            .animation(.spring(duration: 0.35), value: model.lastUpload?.localId)
            .animation(.easeOut(duration: 0.24), value: model.announcement?.message)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(palette.background.ignoresSafeArea())
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
        }
        .fullScreenCover(isPresented: $showCamera) {
            CameraPicker { data in
                let stamp = Int(Date().timeIntervalSince1970)
                model.upload(data, filename: "photo-\(stamp).jpg", mime: "image/jpeg")
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

    private func modePicker(_ palette: JuicePalette) -> some View {
        Picker("Upload mode", selection: $mode) {
            ForEach(UploadMode.allCases) { item in
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
                    in: 0...Double(max(1, options.count - 1)),
                    step: 1
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
        if mode == .media {
            PhotosPicker(selection: $photoItem, matching: .images, photoLibrary: .shared()) {
                dropZoneLabel(palette, icon: "photo.on.rectangle.angled", title: "Pick a photo")
            }
            .buttonStyle(.plain)
        } else {
            Button {
                showFileImporter = true
            } label: {
                dropZoneLabel(palette, icon: "arrow.up.to.line", title: "Drop files here")
            }
            .buttonStyle(.plain)
        }
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
        if glass, #available(iOS 26.0, *) {
            Color.clear
                .glassEffect(
                    .regular,
                    in: RoundedRectangle(cornerRadius: 24, style: .continuous)
                )
        } else {
            palette.surfaceContainerLow
                .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        }
    }

    private func actionRow(_ palette: JuicePalette) -> some View {
        HStack(spacing: 10) {
            Button {
                showFileImporter = true
            } label: {
                Label("Files", systemImage: "folder")
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                    .frame(maxWidth: .infinity)
            }
            PhotosPicker(selection: $photoItem, matching: .images, photoLibrary: .shared()) {
                Label("Photos", systemImage: "photo.on.rectangle")
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                    .frame(maxWidth: .infinity)
            }
            Button {
                openCamera()
            } label: {
                Label("Camera", systemImage: "camera")
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                    .frame(maxWidth: .infinity)
            }
        }
        .buttonStyle(.bordered)
        .controlSize(.large)
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
        ZStack(alignment: .topLeading) {
            if text.isEmpty {
                Text("Paste text or a link")
                    .font(JuiceFont.body(16))
                    .foregroundStyle(palette.onSurfaceVariant.opacity(0.75))
                    .padding(.horizontal, 18)
                    .padding(.vertical, 20)
            }
            TextEditor(text: $text)
                .focused($textFocused)
                .font(JuiceFont.body(16))
                .foregroundStyle(palette.onSurface)
                .scrollContentBackground(.hidden)
                .padding(.horizontal, 13)
                .padding(.vertical, 11)
                .frame(minHeight: 220)
        }
        .background(
            palette.surfaceContainerLow,
            in: RoundedRectangle(cornerRadius: 20, style: .continuous)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .strokeBorder(
                    textFocused ? palette.primary : palette.outline.opacity(0.5),
                    lineWidth: textFocused ? 1.6 : 1
                )
        )
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
                text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            )
        }
        .padding(.horizontal, 20)
    }

    private func progressCard(_ palette: JuicePalette) -> some View {
        GroupBox {
            VStack(alignment: .leading, spacing: 10) {
                ProgressView(value: model.uploadProgress)
                    .tint(palette.primary)
                    .animation(.linear(duration: 0.2), value: model.uploadProgress)
                Text("\(Int(model.uploadProgress * 100))%")
                    .font(JuiceFont.mono(13, weight: .bold))
                    .foregroundStyle(palette.primary)
            }
            .padding(.top, 4)
        } label: {
            Label("Uploading", systemImage: "arrow.up.circle")
                .font(JuiceFont.body(14, weight: .semibold))
                .foregroundStyle(palette.onSurface)
        }
        .padding(.horizontal, 20)
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
