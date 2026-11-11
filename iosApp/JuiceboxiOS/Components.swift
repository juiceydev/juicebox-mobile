import JuiceboxCore
import SwiftUI

// MARK: - Liquid Glass

private struct JuiceGlassEnabledKey: EnvironmentKey {
    static let defaultValue = false
}

extension EnvironmentValues {
    /// Set from the Liquid Glass setting; ignored below iOS 26.
    var juiceGlass: Bool {
        get { self[JuiceGlassEnabledKey.self] }
        set { self[JuiceGlassEnabledKey.self] = newValue }
    }
}

private struct JuiceGlassModifier: ViewModifier {
    let enabled: Bool
    let radius: CGFloat

    @ViewBuilder
    func body(content: Content) -> some View {
        if enabled, #available(iOS 26.0, *) {
            content.glassEffect(
                .regular,
                in: RoundedRectangle(cornerRadius: radius, style: .continuous)
            )
        } else {
            content
        }
    }
}

extension View {
    func juiceGlass(enabled: Bool, radius: CGFloat = 22) -> some View {
        modifier(JuiceGlassModifier(enabled: enabled, radius: radius))
    }
}

enum AppTab: Hashable {
    case upload
    case files
    case settings
}

struct Masthead: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let palette = model.palette(scheme)
        HStack(spacing: 10) {
            Image("Logo")
                .resizable()
                .scaledToFit()
                .frame(width: 40, height: 40)
            Text("JUICEBOX")
                .font(.system(size: 28, weight: .black, design: .rounded))
                .tracking(1.4)
                .foregroundStyle(palette.primary)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Spacer(minLength: 8)
            Button {
                Task { await model.connect() }
            } label: {
                ConnectionBadge(
                    text: model.statusText,
                    state: model.connection,
                    palette: palette
                )
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 20)
    }
}

struct ConnectionBadge: View {
    let text: String
    let state: ConnectionState
    let palette: JuicePalette

    private var dot: Color {
        if state.connecting { return palette.primary }
        if state.connected { return palette.success }
        return palette.error
    }

    var body: some View {
        HStack(spacing: 6) {
            Circle()
                .fill(dot)
                .frame(width: 7, height: 7)
            Text(text)
                .font(.system(size: 11, weight: .semibold, design: .monospaced))
                .lineLimit(1)
        }
        .foregroundStyle(palette.onSurfaceVariant)
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(palette.surfaceContainerHigh, in: Capsule())
    }
}

struct AnnouncementBar: View {
    let announcement: SiteAnnouncement
    let palette: JuicePalette
    @Environment(\.openURL) private var openURL

    var body: some View {
        Button {
            if let raw = announcement.linkUrl, let url = URL(string: raw) {
                openURL(url)
            }
        } label: {
            HStack(spacing: 8) {
                Image(systemName: "info.circle")
                    .font(.system(size: 12, weight: .semibold))
                Text(announcement.message ?? "")
                    .font(.system(size: 12, weight: .medium))
                    .lineLimit(1)
                Spacer(minLength: 0)
                if let link = announcement.linkUrl, !link.isEmpty {
                    Image(systemName: "arrow.up.right")
                        .font(.system(size: 10, weight: .bold))
                }
            }
            .foregroundStyle(palette.onSurface)
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .background(palette.success.opacity(0.14), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

struct JuiceTabBar: View {
    @Binding var selection: AppTab
    let palette: JuicePalette
    var onSelect: (AppTab) -> Void = { _ in }

    @Environment(\.juiceGlass) private var glass

    var body: some View {
        HStack(spacing: 6) {
            tab(.upload, icon: "arrow.up.doc", title: "Upload")
            tab(.files, icon: "folder", title: "Files")
            tab(.settings, icon: "slider.horizontal.3", title: "Settings")
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
        .padding(.bottom, 4)
        .background {
            if !glass {
                palette.background
            }
        }
        .juiceGlass(enabled: glass, radius: 0)
        .overlay(alignment: .top) {
            Rectangle()
                .fill(palette.outline.opacity(glass ? 0.12 : 0.25))
                .frame(height: 1)
        }
    }

    private func tab(_ tab: AppTab, icon: String, title: String) -> some View {
        let selected = selection == tab
        return Button {
            guard !selected else { return }
            selection = tab
            onSelect(tab)
        } label: {
            VStack(spacing: 4) {
                Image(systemName: icon)
                    .font(.system(size: 17, weight: .semibold))
                Text(title)
                    .font(.system(size: 11, weight: .semibold))
            }
            .foregroundStyle(selected ? palette.primary : palette.onSurfaceVariant)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 7)
            .background(
                selected ? palette.primary.opacity(0.12) : Color.clear,
                in: RoundedRectangle(cornerRadius: 18, style: .continuous)
            )
        }
        .buttonStyle(.plain)
    }
}

struct SectionCard<Content: View>: View {
    let palette: JuicePalette
    @Environment(\.juiceGlass) private var glass
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            content
        }
        .background {
            if !glass {
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .fill(palette.surfaceContainer)
            }
        }
        .juiceGlass(enabled: glass)
    }
}

struct SectionLabel: View {
    let text: String
    let palette: JuicePalette

    var body: some View {
        Text(text.uppercased())
            .font(.system(size: 11, weight: .bold, design: .monospaced))
            .tracking(1)
            .foregroundStyle(palette.onSurfaceVariant)
            .padding(.horizontal, 20)
            .padding(.bottom, 8)
    }
}

struct ExpiryBar: View {
    let fraction: Double
    let color: Color
    let track: Color
    var height: CGFloat = 4

    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .leading) {
                Capsule().fill(track)
                Capsule()
                    .fill(color)
                    .frame(width: max(0, geometry.size.width * fraction))
            }
        }
        .frame(height: height)
    }
}

struct ActionChip: View {
    let icon: String
    let title: String
    let palette: JuicePalette
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 7) {
                Image(systemName: icon)
                    .font(.system(size: 13, weight: .semibold))
                Text(title)
                    .font(.system(size: 14, weight: .semibold))
            }
            .foregroundStyle(palette.onSurface)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 13)
            .background(palette.surfaceContainerHigh, in: Capsule())
        }
        .buttonStyle(.plain)
    }
}

struct ToastView: View {
    let message: String
    let palette: JuicePalette

    var body: some View {
        Text(message)
            .font(.system(size: 13, weight: .semibold))
            .foregroundStyle(palette.background)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(palette.onSurface, in: Capsule())
            .shadow(color: .black.opacity(0.18), radius: 12, y: 4)
            .padding(.top, 8)
    }
}

struct JuiceEmptyState: View {
    let icon: String
    let title: String
    let detail: String
    let palette: JuicePalette

    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: icon)
                .font(.system(size: 34, weight: .light))
                .foregroundStyle(palette.primary)
            Text(title)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(palette.onSurface)
            Text(detail)
                .font(.system(size: 13))
                .multilineTextAlignment(.center)
                .foregroundStyle(palette.onSurfaceVariant)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 44)
    }
}
