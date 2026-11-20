import JuiceboxCore
import SwiftUI

struct FilesView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.colorScheme) private var scheme

    @State private var query = ""
    @State private var path: [String] = []

    private var filtered: [LibraryFile] {
        let trimmed = query.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return model.files }
        return model.files.filter {
            $0.filename.localizedCaseInsensitiveContains(trimmed)
        }
    }

    private var totalBytes: Int64 {
        model.files.reduce(0) { $0 + $1.sizeBytes }
    }

    var body: some View {
        let palette = model.palette(scheme)
        NavigationStack(path: $path) {
            List {
                Section {
                    ForEach(filtered, id: \.localId) { file in
                        FileRow(file: file, palette: palette)
                            .listRowBackground(ListRowSurface(palette: palette))
                    }
                } footer: {
                    if !model.files.isEmpty {
                        Text("\(model.files.count) files, \(JuiceFormat.bytes(totalBytes))")
                    }
                }
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .background(palette.background.ignoresSafeArea())
            .tint(palette.primary)
            .navigationTitle("Files")
            .searchable(
                text: $query,
                placement: .navigationBarDrawer(displayMode: .always),
                prompt: "Search"
            )
            .navigationDestination(for: String.self) { id in
                if let file = model.files.first(where: { $0.localId == id }) {
                    FileDetailView(file: file)
                }
            }
            .overlay {
                if filtered.isEmpty {
                    if model.files.isEmpty {
                        ContentUnavailableView(
                            "Nothing here yet",
                            systemImage: "shippingbox",
                            description: Text(
                                "Upload a file and it will show up in this library."
                            )
                        )
                    } else {
                        ContentUnavailableView.search(text: query)
                    }
                }
            }
            .refreshable {
                await model.refreshLibrary()
            }
            .onAppear(perform: openStartFileIfNeeded)
        }
    }

    /// Screenshot runs can open the first file with
    /// SIMCTL_CHILD_JUICEBOX_START_FILE=1.
    private func openStartFileIfNeeded() {
        guard path.isEmpty,
              ProcessInfo.processInfo.environment["JUICEBOX_START_FILE"] == "1" else { return }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
            guard path.isEmpty, let file = model.files.first else { return }
            path = [file.localId]
        }
    }
}

struct FileRow: View {
    @Environment(\.openURL) private var openURL

    let file: LibraryFile
    let palette: JuicePalette

    private var isFailed: Bool {
        file.status == "error" || file.status == "cancelled"
    }

    private var isPending: Bool {
        file.status == "queued" || file.status == "uploading"
    }

    private var isAudio: Bool { file.mimeType.hasPrefix("audio/") }

    var body: some View {
        VStack(alignment: .leading, spacing: 9) {
            HStack(spacing: 12) {
                NavigationLink(value: file.localId) {
                    HStack(spacing: 12) {
                        FileThumbnail(file: file, palette: palette)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(file.filename.isEmpty ? "Untitled" : file.filename)
                                .font(JuiceFont.body(16, weight: .semibold))
                                .foregroundStyle(palette.onSurface)
                                .lineLimit(1)
                            Text(meta)
                                .font(JuiceFont.mono(12))
                                .foregroundStyle(
                                    isFailed ? palette.error : palette.onSurfaceVariant
                                )
                                .lineLimit(1)
                        }
                    }
                }
                .buttonStyle(.plain)

                if isAudio {
                    Button {
                        let link = file.url.isEmpty ? file.rawUrl : file.url
                        if let url = URL(string: link) { openURL(url) }
                    } label: {
                        Image(systemName: "play.circle.fill")
                            .font(.system(size: 24))
                            .foregroundStyle(palette.primary)
                    }
                    .buttonStyle(.borderless)
                    .accessibilityLabel("Play \(file.filename)")
                }
            }

            if !isFailed && !isPending && file.expiresAt > 0 {
                ExpiryBar(
                    fraction: JuiceFormat.expiryFraction(
                        uploadedAt: file.uploadedAt,
                        expiresAt: file.expiresAt
                    ),
                    color: palette.primary,
                    track: palette.outline.opacity(0.25)
                )
                .padding(.leading, 56)
            }
        }
        .padding(.vertical, 4)
    }

    private var meta: String {
        if isFailed {
            return file.error?.isEmpty == false ? (file.error ?? "Failed") : "Failed"
        }
        if isPending { return "Waiting to upload" }
        let size = JuiceFormat.bytes(file.sizeBytes)
        let left = JuiceFormat.timeLeft(expiresAt: file.expiresAt)
        return "\(size), \(left)"
    }
}

/// A real preview for images and video; every other type keeps a tappable
/// symbol tile so all file cards still lead to the detail screen.
struct FileThumbnail: View {
    let file: LibraryFile
    let palette: JuicePalette
    var size: CGFloat = 44

    private var hasPreview: Bool {
        (file.mimeType.hasPrefix("image/") || file.mimeType.hasPrefix("video/")) &&
            !file.rawUrl.isEmpty
    }

    var body: some View {
        ZStack {
            palette.surfaceContainerHigh
            if hasPreview, let url = URL(string: file.rawUrl) {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().scaledToFill()
                    default:
                        symbol
                    }
                }
            } else {
                symbol
            }
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: size * 0.34, style: .continuous))
    }

    private var symbol: some View {
        Image(systemName: JuiceFormat.mimeSymbol(file.mimeType))
            .font(.system(size: size * 0.39, weight: .medium))
            .foregroundStyle(palette.onSurfaceVariant)
    }
}
