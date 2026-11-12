import ActivityKit
import SwiftUI
import WidgetKit

struct UploadLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: UploadActivityAttributes.self) { context in
            LockScreenUploadView(context: context)
                .activityBackgroundTint(Color(hex: 0x1A1613).opacity(0.92))
                .activitySystemActionForegroundColor(Color(hex: 0xF5EDEA))
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    UploadIcon(phase: context.state.phase, size: 18)
                        .frame(width: 28, height: 28)
                        .padding(.leading, 4)
                        .padding(.top, 2)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text(percentText(context.state))
                        .font(.system(size: 15, weight: .bold, design: .rounded))
                        .monospacedDigit()
                        .foregroundStyle(Color(hex: 0xF97316))
                        .padding(.trailing, 4)
                        .padding(.top, 2)
                }
                DynamicIslandExpandedRegion(.center) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(context.state.filename.isEmpty ? "Upload" : context.state.filename)
                            .font(.system(size: 14, weight: .semibold))
                            .lineLimit(1)
                        Text(detailText(context.state, server: context.attributes.server))
                            .font(.system(size: 11))
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                    .padding(.horizontal, 4)
                    .padding(.top, 2)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    ProgressView(value: min(1, max(0, context.state.progress)))
                        .tint(Color(hex: 0xF97316))
                        .padding(.horizontal, 8)
                        .padding(.top, 6)
                        .padding(.bottom, 8)
                }
            } compactLeading: {
                UploadIcon(phase: context.state.phase, size: 13)
            } compactTrailing: {
                Text(percentText(context.state))
                    .font(.system(size: 12, weight: .bold, design: .rounded))
                    .monospacedDigit()
                    .foregroundStyle(Color(hex: 0xF97316))
            } minimal: {
                UploadIcon(phase: context.state.phase, size: 14)
            }
            .keylineTint(Color(hex: 0xF97316))
        }
    }
}

private struct LockScreenUploadView: View {
    let context: ActivityViewContext<UploadActivityAttributes>

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                UploadIcon(phase: context.state.phase, size: 16)
                    .frame(width: 30, height: 30)
                    .background(Color(hex: 0xF97316).opacity(0.18), in: RoundedRectangle(cornerRadius: 9, style: .continuous))

                VStack(alignment: .leading, spacing: 2) {
                    Text(context.state.filename.isEmpty ? "Upload" : context.state.filename)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(Color(hex: 0xF5EDEA))
                        .lineLimit(1)
                    Text(detailText(context.state, server: context.attributes.server))
                        .font(.system(size: 11, design: .monospaced))
                        .foregroundStyle(Color(hex: 0xA29188))
                        .lineLimit(1)
                }
                Spacer(minLength: 6)
                Text(percentText(context.state))
                    .font(.system(size: 14, weight: .bold, design: .rounded))
                    .monospacedDigit()
                    .foregroundStyle(Color(hex: 0xF97316))
            }

            ProgressView(value: min(1, max(0, context.state.progress)))
                .tint(Color(hex: 0xF97316))
        }
        .padding(14)
    }
}

private struct UploadIcon: View {
    let phase: UploadActivityAttributes.ContentState.Phase
    let size: CGFloat

    private var symbol: String {
        switch phase {
        case .uploading: return "arrow.up.circle.fill"
        case .finished: return "checkmark.circle.fill"
        case .failed: return "exclamationmark.circle.fill"
        }
    }

    private var tint: Color {
        switch phase {
        case .uploading: return Color(hex: 0xF97316)
        case .finished: return Color(hex: 0x22C55E)
        case .failed: return Color(hex: 0xF87171)
        }
    }

    var body: some View {
        Image(systemName: symbol)
            .font(.system(size: size, weight: .semibold))
            .foregroundStyle(tint)
    }
}

private func percentText(_ state: UploadActivityAttributes.ContentState) -> String {
    switch state.phase {
    case .finished: return "100%"
    case .failed: return "!"
    case .uploading:
        if state.progress > 0 && state.progress < 0.01 { return "<1%" }
        return "\(Int((state.progress * 100).rounded()))%"
    }
}

private func detailText(_ state: UploadActivityAttributes.ContentState, server: String) -> String {
    switch state.phase {
    case .finished:
        if let link = state.link, !link.isEmpty { return link }
        return "Uploaded to \(server)"
    case .failed:
        return "Upload failed"
    case .uploading:
        if state.totalBytes <= 0 {
            return "Fetching to \(server)"
        }
        let sent = ByteCountFormatter.string(fromByteCount: state.bytesSent, countStyle: .file)
        let total = ByteCountFormatter.string(fromByteCount: state.totalBytes, countStyle: .file)
        return "\(sent) of \(total) to \(server)"
    }
}
