import Foundation
import JuiceboxCore
import SwiftUI

/// The iOS counterpart of Android's session sheet: every upload started from
/// this device in the recent window, with its live state and links.
struct SessionSheet: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dismiss) private var dismiss

    @State private var qrTarget: QrTarget?

    private struct QrTarget: Identifiable {
        let id = UUID()
        let value: String
    }

    var body: some View {
        let palette = model.palette(scheme)
        NavigationStack {
            Group {
                if model.sessionItems.isEmpty {
                    ContentUnavailableView(
                        "No uploads yet",
                        systemImage: "tray",
                        description: Text("Uploads from this device appear here.")
                    )
                } else {
                    List {
                        ForEach(model.sessionItems) { item in
                            card(item, palette: palette)
                                .listRowBackground(ListRowSurface(palette: palette))
                        }
                    }
                    .listStyle(.insetGrouped)
                    .scrollContentBackground(.hidden)
                }
            }
            .background(palette.background.ignoresSafeArea())
            .navigationTitle("This session")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Clear") { model.clearSessionUploads() }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
            .sheet(item: $qrTarget) { target in
                VStack(spacing: 14) {
                    QrCodeView(value: target.value)
                    Text("Scan to open this link")
                        .font(JuiceFont.mono(11))
                        .foregroundStyle(.secondary)
                }
                .padding()
                .presentationDetents([.medium])
            }
        }
    }

    @ViewBuilder
    private func card(_ item: UploadSessionItem, palette: JuicePalette) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            HStack(spacing: 10) {
                Image(systemName: statusIcon(item))
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(statusColor(item, palette: palette))
                    .frame(width: 24)
                VStack(alignment: .leading, spacing: 2) {
                    Text(item.name.isEmpty ? "Upload" : item.name)
                        .font(JuiceFont.body(15, weight: .semibold))
                        .foregroundStyle(palette.onSurface)
                        .lineLimit(1)
                    Text(statusText(item))
                        .font(JuiceFont.mono(11))
                        .foregroundStyle(palette.onSurfaceVariant)
                }
                Spacer(minLength: 4)
                Text(JuiceFormat.bytes(item.sizeBytes))
                    .font(JuiceFont.mono(11))
                    .foregroundStyle(palette.onSurfaceVariant)
            }

            if item.isActive {
                ProgressView(value: item.fraction)
                    .tint(palette.primary)
                HStack {
                    Text(percent(item))
                        .font(JuiceFont.mono(11, weight: .bold))
                        .foregroundStyle(palette.primary)
                    Spacer()
                    if item.speed > 0 {
                        Text("\(JuiceFormat.bytes(Int64(item.speed)))/s")
                            .font(JuiceFont.mono(11))
                            .foregroundStyle(palette.onSurfaceVariant)
                    }
                }
            }

            if let error = item.error, !error.isEmpty {
                Text(error)
                    .font(JuiceFont.mono(11))
                    .foregroundStyle(palette.error)
            }

            let link = model.link(item, variant: model.settings.linkVariant)
            if !link.isEmpty {
                HStack(spacing: 8) {
                    if let url = URL(string: link) {
                        Link(link, destination: url)
                    } else {
                        Text(link)
                    }
                    Spacer(minLength: 4)
                    Button {
                        model.copy(link)
                    } label: {
                        Image(systemName: "doc.on.doc")
                    }
                    .buttonStyle(.borderless)
                    .accessibilityLabel("Copy link")
                    Button {
                        qrTarget = QrTarget(value: link)
                    } label: {
                        Image(systemName: "qrcode")
                    }
                    .buttonStyle(.borderless)
                    .accessibilityLabel("Show QR code")
                    if let libraryId = item.libraryId,
                       let file = model.files.first(where: { $0.localId == libraryId }) {
                        Button(role: .destructive) {
                            Task { _ = await model.delete(file) }
                            model.removeSessionItem(item.id)
                        } label: {
                            Image(systemName: "trash")
                        }
                        .buttonStyle(.borderless)
                        .accessibilityLabel("Delete file")
                    }
                }
                .font(JuiceFont.mono(11))
                .lineLimit(1)
            } else if item.isActive {
                Text("Link appears when the server reserves it")
                    .font(JuiceFont.mono(11))
                    .foregroundStyle(palette.onSurfaceVariant)
            }
        }
        .padding(.vertical, 4)
    }

    private func statusIcon(_ item: UploadSessionItem) -> String {
        if item.isDone { return "checkmark.circle.fill" }
        if item.error != nil { return "exclamationmark.circle" }
        return "arrow.up.circle"
    }

    private func statusColor(_ item: UploadSessionItem, palette: JuicePalette) -> Color {
        if item.isDone { return palette.primary }
        if item.error != nil { return palette.error }
        return palette.onSurfaceVariant
    }

    private func statusText(_ item: UploadSessionItem) -> String {
        if item.isDone { return "Done" }
        if let error = item.error, !error.isEmpty { return error }
        if item.status == "queued" { return "Starting" }
        return "Uploading"
    }

    private func percent(_ item: UploadSessionItem) -> String {
        let value = item.fraction * 100
        if value < 10 { return String(format: "%.1f%%", value) }
        return "\(Int(value))%"
    }
}
