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

    init(file: LibraryFile) {
        _stored = State(initialValue: file)
    }

    /// Reads go through a plain property: the read-only Kotlin fields cannot be
    /// reached through @State's writable key-path lookup.
    private var current: LibraryFile { stored }

    var body: some View {
        let palette = model.palette(scheme)
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                header(palette)
                linksCard(palette)
                qrCard(palette)
                actions(palette)
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 32)
        }
        .background(palette.background.ignoresSafeArea())
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
            HStack {
                Spacer()
                Button {
                    dismiss()
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(palette.onSurfaceVariant)
                        .frame(width: 32, height: 32)
                        .background(palette.surfaceContainerHigh, in: Circle())
                }
                .buttonStyle(.plain)
            }

            HStack(spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .fill(palette.surfaceContainerHigh)
                        .frame(width: 60, height: 60)
                    Image(systemName: JuiceFormat.mimeSymbol(current.mimeType))
                        .font(.system(size: 24, weight: .medium))
                        .foregroundStyle(palette.onSurfaceVariant)
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text(current.filename.isEmpty ? "Untitled" : current.filename)
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(palette.onSurface)
                        .lineLimit(2)
                    Text("\(JuiceFormat.bytes(current.sizeBytes)) · \(JuiceFormat.mimeLabel(current.mimeType, filename: current.filename))")
                        .font(.system(size: 12, design: .monospaced))
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
                    .font(.system(size: 11, design: .monospaced))
                    .foregroundStyle(palette.onSurfaceVariant)
                }
            }

            HStack(spacing: 8) {
                if current.protected {
                    badge("Locked", icon: "lock.fill", palette: palette)
                }
                if current.isEncrypted {
                    badge("Encrypted", icon: "shield.lefthalf.filled", palette: palette)
                }
                if current.status == "ready" {
                    badge("Ready", icon: "checkmark.seal", palette: palette)
                }
            }
        }
        .padding(.top, 18)
    }

    private func badge(_ text: String, icon: String, palette: JuicePalette) -> some View {
        HStack(spacing: 5) {
            Image(systemName: icon)
                .font(.system(size: 10, weight: .semibold))
            Text(text)
                .font(.system(size: 11, weight: .semibold))
        }
        .foregroundStyle(palette.onSurfaceVariant)
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(palette.surfaceContainerHigh, in: Capsule())
    }

    private func linksCard(_ palette: JuicePalette) -> some View {
        SectionCard(palette: palette) {
            VStack(spacing: 0) {
                linkRow("Preview", variant: "preview", palette: palette)
                Divider().overlay(palette.outline.opacity(0.25)).padding(.leading, 16)
                linkRow("Raw", variant: "raw", palette: palette)
                Divider().overlay(palette.outline.opacity(0.25)).padding(.leading, 16)
                linkRow("Download", variant: "download", palette: palette)
            }
        }
    }

    private func linkRow(_ title: String, variant: String, palette: JuicePalette) -> some View {
        let link = model.link(current, variant: variant)
        return HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(palette.onSurface)
                Text(link)
                    .font(.system(size: 11, design: .monospaced))
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
            .buttonStyle(.plain)
            if let url = URL(string: link) {
                ShareLink(item: url) {
                    Image(systemName: "square.and.arrow.up")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(palette.primary)
                        .frame(width: 32, height: 32)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }

    @ViewBuilder
    private func qrCard(_ palette: JuicePalette) -> some View {
        let link = model.link(current, variant: model.settings.linkVariant)
        if let image = qrImage(link) {
            SectionCard(palette: palette) {
                VStack(spacing: 12) {
                    Image(uiImage: image)
                        .interpolation(.none)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 190, height: 190)
                        .padding(10)
                        .background(Color.white, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    Text("Scan to open the \(model.settings.linkVariant) link")
                        .font(.system(size: 11, design: .monospaced))
                        .foregroundStyle(palette.onSurfaceVariant)
                }
                .frame(maxWidth: .infinity)
                .padding(18)
            }
        }
    }

    private func actions(_ palette: JuicePalette) -> some View {
        VStack(spacing: 10) {
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
                        ProgressView().tint(palette.onSurface)
                    } else {
                        Image(systemName: "arrow.clockwise")
                            .font(.system(size: 13, weight: .semibold))
                    }
                    Text("Refresh info")
                        .font(.system(size: 15, weight: .semibold))
                }
                .foregroundStyle(palette.onSurface)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(palette.surfaceContainerHigh, in: Capsule())
            }
            .buttonStyle(.plain)
            .disabled(busy)

            Button {
                confirmDelete = true
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: "trash")
                        .font(.system(size: 13, weight: .semibold))
                    Text("Delete file")
                        .font(.system(size: 15, weight: .semibold))
                }
                .foregroundStyle(palette.error)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(palette.error.opacity(0.1), in: Capsule())
            }
            .buttonStyle(.plain)
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
