import JuiceboxCore
import PhotosUI
import SwiftUI
import UIKit
import UniformTypeIdentifiers

struct UploadView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.colorScheme) private var scheme

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
                }

                if let announcement = model.announcement,
                   let message = announcement.message,
                   !message.isEmpty {
                    AnnouncementBar(announcement: announcement, palette: palette)
                        .padding(.horizontal, 16)
                }

                modePicker(palette)
                retention(palette)

                if mode == .text {
                    textEditor(palette)
                } else {
                    dropZone(palette)
                }

                if mode == .files {
                    actionRow(palette)
                }

                if mode == .text {
                    saveRow(palette)
                }

                if model.isUploading {
                    progressCard(palette)
                }

                if let file = model.lastUpload {
                    resultCard(file, palette)
                }

                if model.settings.lockUploads {
                    lockNote(palette)
                }
            }
            .padding(.bottom, 28)
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
                    .font(JuiceFont.body(14, weight: .medium))
                    .foregroundStyle(palette.primary)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
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
        HStack(spacing: 12) {
            ForEach(UploadMode.allCases) { item in
                Button {
                    mode = item
                    textFocused = item == .text
                } label: {
                    VStack(spacing: 6) {
                        Text(item.rawValue)
                            .font(JuiceFont.body(14, weight: mode == item ? .semibold : .medium))
                        Capsule()
                            .fill(mode == item ? palette.primary : Color.clear)
                            .frame(width: 48, height: 3)
                    }
                    .foregroundStyle(mode == item ? palette.primary : palette.onSurfaceVariant)
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 20)
    }

    private func retention(_ palette: JuicePalette) -> some View {
        RetentionSlider(
            options: model.ttlOptions,
            minutes: model.selectedMinutes,
            palette: palette,
            haptics: model.settings.haptics
        ) { minutes in
            model.selectedMinutes = minutes
            model.setLastTtl(minutes)
        }
        .padding(.horizontal, 20)
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
        VStack(spacing: 10) {
            Image(systemName: icon)
                .font(.system(size: 36, weight: .regular))
                .foregroundStyle(palette.primary)
            Text(title)
                .font(JuiceFont.body(22, weight: .semibold))
                .foregroundStyle(palette.onSurface)
            Text("Tap to browse")
                .font(JuiceFont.body(14))
                .foregroundStyle(palette.onSurfaceVariant)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 210)
        .background {
            ZStack {
                palette.surfaceContainerLow
                DropZoneStripes(color: palette.primary.opacity(0.02))
            }
            .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
        }
        .overlay(
            RoundedRectangle(cornerRadius: 26, style: .continuous)
                .strokeBorder(
                    palette.primary.opacity(0.55),
                    style: StrokeStyle(lineWidth: 1.4, dash: [7, 6])
                )
        )
        .padding(.horizontal, 20)
    }

    private func actionRow(_ palette: JuicePalette) -> some View {
        HStack(spacing: 8) {
            ActionChip(icon: "folder", title: "Files", palette: palette) {
                showFileImporter = true
            }
            PhotosPicker(selection: $photoItem, matching: .images, photoLibrary: .shared()) {
                pickerLabel("photo.on.rectangle", title: "Photos", palette: palette)
            }
            .buttonStyle(.plain)
            ActionChip(icon: "camera", title: "Camera", palette: palette) {
                openCamera()
            }
        }
        .padding(.horizontal, 20)
    }

    private func pickerLabel(_ icon: String, title: String, palette: JuicePalette) -> some View {
        HStack(spacing: 7) {
            Image(systemName: icon)
                .font(.system(size: 13, weight: .semibold))
            Text(title)
                .font(JuiceFont.body(14, weight: .semibold))
        }
        .foregroundStyle(palette.onSurface)
        .frame(maxWidth: .infinity)
        .padding(.vertical, 13)
        .background(palette.surfaceContainerHigh, in: Capsule())
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
            palette.surfaceContainer,
            in: RoundedRectangle(cornerRadius: 24, style: .continuous)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
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
                HStack(spacing: 7) {
                    Image(systemName: "checkmark")
                        .font(.system(size: 13, weight: .bold))
                    Text("Save text")
                        .font(JuiceFont.body(15, weight: .semibold))
                }
                .foregroundStyle(palette.onPrimary)
                .padding(.horizontal, 18)
                .padding(.vertical, 12)
                .background(palette.primary, in: Capsule())
            }
            .buttonStyle(.plain)
            .disabled(model.isUploading || text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            .opacity(model.isUploading || text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? 0.5 : 1)
        }
        .padding(.horizontal, 20)
    }

    private func progressCard(_ palette: JuicePalette) -> some View {
        SectionCard(palette: palette) {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text("Uploading")
                        .font(JuiceFont.body(14, weight: .semibold))
                        .foregroundStyle(palette.onSurface)
                    Spacer()
                    Text("\(Int(model.uploadProgress * 100))%")
                        .font(JuiceFont.mono(13, weight: .bold))
                        .foregroundStyle(palette.primary)
                }
                ProgressView(value: model.uploadProgress)
                    .tint(palette.primary)
            }
            .padding(18)
        }
        .padding(.horizontal, 20)
    }

    private func resultCard(_ file: LibraryFile, _ palette: JuicePalette) -> some View {
        let link = model.link(file, variant: model.settings.linkVariant)
        return SectionCard(palette: palette) {
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
                        resultAction("Copy", icon: "doc.on.doc", palette: palette)
                    }
                    .buttonStyle(.plain)

                    if let url = URL(string: link) {
                        ShareLink(item: url) {
                            resultAction("Share", icon: "square.and.arrow.up", palette: palette)
                        }
                        .buttonStyle(.plain)
                    }

                    if let url = URL(string: link) {
                        Link(destination: url) {
                            resultAction("Open", icon: "arrow.up.right", palette: palette)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(18)
        }
        .padding(.horizontal, 20)
    }

    private func resultAction(_ title: String, icon: String, palette: JuicePalette) -> some View {
        HStack(spacing: 6) {
            Image(systemName: icon)
                .font(.system(size: 11, weight: .semibold))
            Text(title)
                .font(JuiceFont.body(12, weight: .semibold))
        }
        .foregroundStyle(palette.onSurface)
        .frame(maxWidth: .infinity)
        .padding(.vertical, 9)
        .background(palette.surfaceContainerHigh, in: Capsule())
    }

    private func lockNote(_ palette: JuicePalette) -> some View {
        HStack(spacing: 7) {
            Image(systemName: "lock.fill")
                .font(.system(size: 11))
            Text("Uploads are password-protected from this device")
                .font(JuiceFont.body(12))
        }
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

/// The site's -45 degree stripes, painted faintly behind the drop zone.
private struct DropZoneStripes: View {
    let color: Color

    var body: some View {
        Canvas { context, size in
            let period: CGFloat = 15
            let band = period * 0.42
            let span = (size.width + size.height) * 1.6
            context.translateBy(x: size.width / 2, y: size.height / 2)
            context.rotate(by: .degrees(-45))
            context.translateBy(x: -size.width / 2, y: -size.height / 2)
            var x = -span
            while x < span {
                let rect = CGRect(x: x, y: -span, width: band, height: span * 2)
                context.fill(Path(rect), with: .color(color))
                x += period
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
