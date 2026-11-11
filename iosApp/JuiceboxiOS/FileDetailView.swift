import CoreImage.CIFilterBuiltins
import JuiceboxCore
import SwiftUI
import UIKit

struct FileDetailView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dismiss) private var dismiss

    @State private var stored: LibraryFile
    @State private var confirmDelete = false
    @State private var busy = false

    private let variants = [
        ("Preview", "preview"),
        ("Raw", "raw"),
        ("Download", "download"),
    ]

    init(file: LibraryFile) {
        _stored = State(initialValue: file)
    }

    /// Reads go through a plain property: the read-only Kotlin fields cannot be
    /// reached through @State's writable key-path lookup.
    private var current: LibraryFile { stored }

    var body: some View {
        let palette = model.palette(scheme)
        List {
            Section {
                header(palette)
            }
            .listRowBackground(ListRowSurface(palette: palette))

            Section("Links") {
                ForEach(variants.indices, id: \.self) { index in
                    linkRow(
                        variants[index].0,
                        variant: variants[index].1,
                        palette: palette
                    )
                }
            }
            .listRowBackground(ListRowSurface(palette: palette))

            qrSection(palette)

            Section {
                Button {
                    Task {
                        busy = true
                        if let updated = await model.refresh(current) {
                            stored = updated
                        }
                        busy = false
                    }
                } label: {
                    HStack(spacing: 8) {
                        if busy {
                            ProgressView()
                        } else {
                            Image(systemName: "arrow.clockwise")
                        }
                        Text("Refresh info")
                    }
                    .frame(maxWidth: .infinity)
                }
                .disabled(busy)

                Button(role: .destructive) {
                    confirmDelete = true
                } label: {
                    Label("Delete file", systemImage: "trash")
                        .frame(maxWidth: .infinity)
                }
            }
            .listRowBackground(ListRowSurface(palette: palette))
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .background(palette.background.ignoresSafeArea())
        .tint(palette.primary)
        .font(JuiceFont.body(16))
        .navigationTitle("File")
        .navigationBarTitleDisplayMode(.inline)
        .confirmationDialog(
            "Delete this file?",
            isPresented: $confirmDelete,
            titleVisibility: .visible
        ) {
            Button("Delete", role: .destructive) {
                Task {
                    let deleted = await model.delete(current)
                    if deleted { dismiss() }
                }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("The link stops working immediately.")
        }
    }

    private func header(_ palette: JuicePalette) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 14) {
                Image(systemName: JuiceFormat.mimeSymbol(current.mimeType))
                    .font(.system(size: 24, weight: .medium))
                    .foregroundStyle(palette.onSurfaceVariant)
                    .frame(width: 60, height: 60)
                    .background(
                        palette.surfaceContainerHigh,
                        in: RoundedRectangle(cornerRadius: 18, style: .continuous)
                    )
                VStack(alignment: .leading, spacing: 4) {
                    Text(current.filename.isEmpty ? "Untitled" : current.filename)
                        .font(JuiceFont.body(20, weight: .bold))
                        .foregroundStyle(palette.onSurface)
                        .lineLimit(2)
                    Text(JuiceFormat.bytes(current.sizeBytes))
                    .font(JuiceFont.mono(12))
                    .foregroundStyle(palette.onSurfaceVariant)
                }
            }

            if current.expiresAt > 0 {
                VStack(alignment: .leading, spacing: 7) {
                    ExpiryBar(
                        fraction: JuiceFormat.expiryFraction(
                            uploadedAt: current.uploadedAt,
                            expiresAt: current.expiresAt
                        ),
                        color: palette.primary,
                        track: palette.outline.opacity(0.25),
                        height: 5
                    )
                    HStack {
                        Text("Expires \(JuiceFormat.expiryDate(current.expiresAt))")
                        Spacer()
                        Text(JuiceFormat.timeLeft(expiresAt: current.expiresAt))
                    }
                    .font(JuiceFont.mono(11))
                    .foregroundStyle(palette.onSurfaceVariant)
                }
            }

            if model.isLocked(current) || current.isEncrypted {
                HStack(spacing: 8) {
                    if model.isLocked(current) {
                        badge("Locked", icon: "lock.fill", palette: palette)
                    }
                    if current.isEncrypted {
                        badge("Encrypted", icon: "shield.lefthalf.filled", palette: palette)
                    }
                }
            }
        }
        .padding(.vertical, 4)
    }

    private func badge(_ text: String, icon: String, palette: JuicePalette) -> some View {
        Label(text, systemImage: icon)
            .font(JuiceFont.body(11, weight: .semibold))
            .foregroundStyle(palette.onSurfaceVariant)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(palette.surfaceContainerHigh, in: Capsule())
    }

    private func linkRow(
        _ title: String,
        variant: String,
        palette: JuicePalette
    ) -> some View {
        let link = model.link(current, variant: variant)
        return HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(JuiceFont.body(13, weight: .semibold))
                    .foregroundStyle(palette.onSurface)
                Text(link)
                    .font(JuiceFont.mono(11))
                    .foregroundStyle(palette.onSurfaceVariant)
                    .lineLimit(1)
                    .truncationMode(.middle)
            }
            Spacer(minLength: 4)
            Button {
                model.copy(link)
            } label: {
                Image(systemName: "doc.on.doc")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(palette.primary)
                    .frame(width: 32, height: 32)
            }
            .buttonStyle(.borderless)
            if let url = URL(string: link) {
                ShareLink(item: url) {
                    Image(systemName: "square.and.arrow.up")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(palette.primary)
                        .frame(width: 32, height: 32)
                }
                .buttonStyle(.borderless)
            }
        }
    }

    @ViewBuilder
    private func qrSection(_ palette: JuicePalette) -> some View {
        let link = model.link(current, variant: model.settings.linkVariant)
        if let image = qrImage(link) {
            Section {
                VStack(spacing: 12) {
                    Image(uiImage: image)
                        .interpolation(.none)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 190, height: 190)
                        .padding(10)
                        .background(
                            Color.white,
                            in: RoundedRectangle(cornerRadius: 16, style: .continuous)
                        )
                    Text("Scan to open the \(model.settings.linkVariant) link")
                        .font(JuiceFont.mono(11))
                        .foregroundStyle(palette.onSurfaceVariant)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 6)
            }
            .listRowBackground(ListRowSurface(palette: palette))
        }
    }

    private func qrImage(_ value: String) -> UIImage? {
        guard !value.isEmpty else { return nil }
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(value.utf8)
        filter.correctionLevel = "M"
        guard let output = filter.outputImage else { return nil }
        let colored = output.applyingFilter(
            "CIFalseColor",
            parameters: [
                "inputColor0": CIColor(color: .black),
                "inputColor1": CIColor(color: .white),
            ]
        )
        let scaled = colored.transformed(by: CGAffineTransform(scaleX: 10, y: 10))
        let context = CIContext()
        guard let cgImage = context.createCGImage(scaled, from: scaled.extent) else { return nil }
        return UIImage(cgImage: cgImage)
    }
}
