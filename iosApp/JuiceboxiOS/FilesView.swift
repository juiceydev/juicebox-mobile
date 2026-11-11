import JuiceboxCore
import SwiftUI

struct FilesView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.colorScheme) private var scheme

    @State private var query = ""
    @State private var selected: SelectedFile?

    private struct SelectedFile: Identifiable {
        let id: String
        let file: LibraryFile
    }

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
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                header(palette)
                searchField(palette)

                if filtered.isEmpty {
                    if model.files.isEmpty {
                        JuiceEmptyState(
                            icon: "shippingbox",
                            title: "Nothing here yet",
                            detail: "Upload a file and it will show up in this library.",
                            palette: palette
                        )
                    } else {
                        JuiceEmptyState(
                            icon: "magnifyingglass",
                            title: "No matches",
                            detail: "No files named \"\(query)\".",
                            palette: palette
                        )
                    }
                } else {
                    LazyVStack(spacing: 0) {
                        ForEach(filtered, id: \.localId) { file in
                            Button {
                                selected = SelectedFile(id: file.localId, file: file)
                            } label: {
                                FileRow(file: file, palette: palette)
                            }
                            .buttonStyle(.plain)
                            if file.localId != filtered.last?.localId {
                                Divider()
                                    .overlay(palette.outline.opacity(0.25))
                                    .padding(.leading, 74)
                            }
                        }
                    }
                    .padding(.bottom, 12)
                }
            }
            .padding(.bottom, 24)
        }
        .refreshable {
            await model.refreshLibrary()
        }
        .background(palette.background.ignoresSafeArea())
        .scrollDismissesKeyboard(.interactively)
        .sheet(item: $selected) { item in
            FileDetailView(file: item.file)
                .environmentObject(model)
        }
    }

    private func header(_ palette: JuicePalette) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Files")
                .font(JuiceFont.body(36))
                .foregroundStyle(palette.onSurface)
            Text("\(model.files.count) files · \(JuiceFormat.bytes(totalBytes))")
                .font(JuiceFont.body(13))
                .foregroundStyle(palette.onSurfaceVariant)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 20)
        .padding(.top, 14)
    }

    private func searchField(_ palette: JuicePalette) -> some View {
        HStack(spacing: 9) {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(palette.onSurfaceVariant)
            TextField("Search", text: $query)
                .font(JuiceFont.body(16))
                .foregroundStyle(palette.onSurface)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            if !query.isEmpty {
                Button {
                    query = ""
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundStyle(palette.onSurfaceVariant)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(palette.surfaceContainer, in: Capsule())
        .overlay(Capsule().strokeBorder(palette.outline.opacity(0.4), lineWidth: 1))
        .padding(.horizontal, 20)
    }
}

struct FileRow: View {
    let file: LibraryFile
    let palette: JuicePalette

    private var isFailed: Bool {
        file.status == "error" || file.status == "cancelled"
    }

    private var isPending: Bool {
        file.status == "queued" || file.status == "uploading"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 9) {
            HStack(spacing: 12) {
                ZStack {
                    RoundedRectangle(cornerRadius: 15, style: .continuous)
                        .fill(palette.surfaceContainerHigh)
                        .frame(width: 44, height: 44)
                    Image(systemName: JuiceFormat.mimeSymbol(file.mimeType))
                        .font(.system(size: 17, weight: .medium))
                        .foregroundStyle(palette.onSurfaceVariant)
                }

                VStack(alignment: .leading, spacing: 3) {
                    Text(file.filename.isEmpty ? "Untitled" : file.filename)
                        .font(JuiceFont.body(16, weight: .semibold))
                        .foregroundStyle(palette.onSurface)
                        .lineLimit(1)
                    Text(meta)
                        .font(JuiceFont.mono(12))
                        .foregroundStyle(isFailed ? palette.error : palette.onSurfaceVariant)
                        .lineLimit(1)
                }
                Spacer(minLength: 4)
                Image(systemName: "chevron.right")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(palette.outline)
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
        .padding(.horizontal, 20)
        .padding(.vertical, 13)
        .contentShape(Rectangle())
    }

    private var meta: String {
        if isFailed {
            return file.error?.isEmpty == false ? (file.error ?? "Failed") : "Failed"
        }
        if isPending { return "Waiting to upload" }
        let size = JuiceFormat.bytes(file.sizeBytes)
        let kind = JuiceFormat.mimeLabel(file.mimeType, filename: file.filename)
        let left = JuiceFormat.timeLeft(expiresAt: file.expiresAt)
        return "\(size) · \(kind) · \(left)"
    }
}
