import SwiftUI
import UIKit

// MARK: - Groups

/// One tonal surface per settings submenu, matching Compose's SettingsGroup.
struct SettingsGroup<Content: View>: View {
    let palette: JuicePalette
    @Environment(\.juiceGlass) private var glass
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background {
            if !glass {
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .fill(palette.surfaceContainerLow)
            }
        }
        .juiceGlass(enabled: glass, radius: 24)
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
    }
}

struct HairlineDivider: View {
    let palette: JuicePalette

    var body: some View {
        Rectangle()
            .fill(palette.outlineVariant)
            .frame(height: 0.5)
    }
}

// MARK: - Rows

struct SettingsNavRow: View {
    let icon: String
    let title: String
    var value: String?
    let palette: JuicePalette
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 16) {
                Image(systemName: icon)
                    .font(.system(size: 18, weight: .regular))
                    .foregroundStyle(palette.primary)
                    .frame(width: 22, height: 22)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(JuiceFont.body(16))
                        .foregroundStyle(palette.onSurface)
                    if let value, !value.isEmpty {
                        Text(value)
                            .font(JuiceFont.body(12))
                            .foregroundStyle(palette.onSurfaceVariant)
                            .lineLimit(1)
                    }
                }
                Spacer(minLength: 8)
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(palette.onSurfaceVariant)
                    .frame(width: 18, height: 18)
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 16)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

struct SettingsToggleRow: View {
    let title: String
    var detail: String?
    let isOn: Bool
    let palette: JuicePalette
    let onChange: (Bool) -> Void

    var body: some View {
        Button {
            onChange(!isOn)
        } label: {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(JuiceFont.body(16))
                        .foregroundStyle(palette.onSurface)
                    if let detail, !detail.isEmpty {
                        Text(detail)
                            .font(JuiceFont.body(12))
                            .foregroundStyle(palette.onSurfaceVariant)
                    }
                }
                Spacer(minLength: 0)
                JuiceSwitch(isOn: isOn, palette: palette)
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 12)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

struct SettingsActionRow: View {
    let icon: String
    let title: String
    var destructive = false
    let palette: JuicePalette
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 16) {
                Image(systemName: icon)
                    .font(.system(size: 18, weight: .regular))
                    .foregroundStyle(destructive ? palette.error : palette.primary)
                    .frame(width: 22, height: 22)
                Text(title)
                    .font(JuiceFont.body(16))
                    .foregroundStyle(destructive ? palette.error : palette.onSurface)
                Spacer(minLength: 8)
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 16)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

struct SettingsInfoRow: View {
    let title: String
    let value: String
    let palette: JuicePalette

    var body: some View {
        HStack(spacing: 12) {
            Text(title)
                .font(JuiceFont.body(14))
                .foregroundStyle(palette.onSurfaceVariant)
            Spacer(minLength: 8)
            Text(value)
                .font(JuiceFont.body(14))
                .foregroundStyle(palette.onSurface)
                .multilineTextAlignment(.trailing)
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 14)
    }
}

struct SettingsChoiceRow: View {
    let title: String
    let value: String
    let palette: JuicePalette
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Text(title)
                    .font(JuiceFont.body(16))
                    .foregroundStyle(palette.onSurface)
                Spacer(minLength: 8)
                Text(value)
                    .font(JuiceFont.body(14))
                    .foregroundStyle(palette.onSurfaceVariant)
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(palette.onSurfaceVariant)
                    .frame(width: 18, height: 18)
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 16)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Choices

/// Material radio row: 20pt ring, 10pt selected dot.
struct ChoiceOption: View {
    let label: String
    let selected: Bool
    let palette: JuicePalette
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                RadioDot(selected: selected, palette: palette)
                    .frame(width: 48, height: 48)
                Text(label)
                    .font(JuiceFont.body(16))
                    .foregroundStyle(palette.onSurface)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

struct RadioDot: View {
    let selected: Bool
    let palette: JuicePalette

    var body: some View {
        ZStack {
            Circle()
                .strokeBorder(
                    selected ? palette.primary : palette.onSurfaceVariant,
                    lineWidth: 2
                )
                .frame(width: 20, height: 20)
            if selected {
                Circle()
                    .fill(palette.primary)
                    .frame(width: 10, height: 10)
            }
        }
        .frame(width: 20, height: 20)
    }
}

/// Theme row: radio, label, three palette swatches.
struct ThemeChoiceRow: View {
    let theme: JuiceTheme.Mode
    let swatches: [Color]
    let selected: Bool
    let palette: JuicePalette
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                RadioDot(selected: selected, palette: palette)
                    .frame(width: 48, height: 48)
                Text(theme.label)
                    .font(JuiceFont.body(16))
                    .foregroundStyle(palette.onSurface)
                Spacer(minLength: 8)
                HStack(spacing: 4) {
                    ForEach(Array(swatches.enumerated()), id: \.offset) { _, color in
                        Circle()
                            .fill(color)
                            .frame(width: 14, height: 14)
                    }
                }
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 2)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Controls

/// Material 3 switch: 52x32 track, 24pt thumb.
struct JuiceSwitch: View {
    let isOn: Bool
    let palette: JuicePalette

    var body: some View {
        ZStack(alignment: isOn ? .trailing : .leading) {
            Capsule()
                .fill(isOn ? palette.primary : palette.surfaceContainerHighest)
                .overlay(
                    Capsule()
                        .strokeBorder(
                            isOn ? Color.clear : palette.outline,
                            lineWidth: 2
                        )
                )
                .frame(width: 52, height: 32)
            Circle()
                .fill(isOn ? palette.onPrimary : palette.outline)
                .frame(width: isOn ? 24 : 16, height: isOn ? 24 : 16)
                .padding(4)
        }
        .frame(width: 52, height: 32)
        .animation(.easeInOut(duration: 0.16), value: isOn)
    }
}

/// Outlined field with a floating label, sized like the Compose one.
struct SettingsField: View {
    let label: String
    var placeholder = ""
    @Binding var text: String
    let palette: JuicePalette
    var labelBackground: Color
    var keyboard: UIKeyboardType = .default
    var secure = false
    var onSubmit: () -> Void = {}

    var body: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .strokeBorder(palette.onSurfaceVariant.opacity(0.6), lineWidth: 1)
            Group {
                if secure {
                    SecureField(placeholder, text: $text)
                } else {
                    TextField(placeholder, text: $text)
                }
            }
            .font(JuiceFont.body(16))
            .foregroundStyle(palette.onSurface)
            .keyboardType(keyboard)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .onSubmit(onSubmit)
            .padding(.horizontal, 16)
            .padding(.top, 9)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            Text(label)
                .font(JuiceFont.body(12))
                .foregroundStyle(palette.onSurfaceVariant)
                .padding(.horizontal, 4)
                .background(labelBackground)
                .offset(x: 12, y: -8)
        }
        .frame(height: 56)
    }
}

struct SettingsTonalButton: View {
    let title: String
    let icon: String
    var expanded = false
    var busy = false
    let palette: JuicePalette
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 7) {
                if busy {
                    ProgressView()
                        .tint(palette.onSurface)
                        .scaleEffect(0.75)
                } else {
                    Image(systemName: icon)
                        .font(.system(size: 14, weight: .medium))
                    Text(title)
                        .font(JuiceFont.body(14, weight: .medium))
                }
            }
            .foregroundStyle(palette.onSurface)
            .padding(.horizontal, 16)
            .frame(maxWidth: expanded ? .infinity : nil)
            .frame(height: 40)
            .background(
                palette.surfaceContainerHighest,
                in: RoundedRectangle(cornerRadius: 12, style: .continuous)
            )
        }
        .buttonStyle(.plain)
        .disabled(busy)
    }
}

struct SettingsPrimaryButton: View {
    let title: String
    let icon: String
    var busy = false
    var enabled = true
    let palette: JuicePalette
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if busy {
                    ProgressView()
                        .tint(palette.onPrimary)
                        .scaleEffect(0.8)
                } else {
                    Image(systemName: icon)
                        .font(.system(size: 15, weight: .semibold))
                    Text(title)
                        .font(JuiceFont.body(15, weight: .medium))
                }
            }
            .foregroundStyle(palette.onPrimary)
            .frame(maxWidth: .infinity)
            .frame(height: 48)
            .background(palette.primary, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(!enabled || busy)
        .opacity(enabled && !busy ? 1 : 0.55)
    }
}

// MARK: - Top bar

struct SettingsTopBar: View {
    let title: String
    let palette: JuicePalette
    let onBack: () -> Void

    var body: some View {
        HStack(spacing: 4) {
            Button(action: onBack) {
                Image(systemName: "arrow.left")
                    .font(.system(size: 18, weight: .medium))
                    .foregroundStyle(palette.onSurface)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            Text(title)
                .font(JuiceFont.body(22))
                .foregroundStyle(palette.onSurface)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 8)
        .frame(height: 56)
    }
}

// MARK: - Dialogs

struct SettingsDialogCard<Content: View>: View {
    let icon: String
    let title: String
    let palette: JuicePalette
    let confirmLabel: String
    var destructive = false
    var confirmEnabled = true
    var showConfirm = true
    let onConfirm: () -> Void
    let onDismiss: () -> Void
    @ViewBuilder var content: Content
    @State private var shown = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Image(systemName: icon)
                .font(.system(size: 22, weight: .regular))
                .foregroundStyle(destructive ? palette.error : palette.primary)
            Text(title)
                .font(JuiceFont.body(22))
                .foregroundStyle(palette.onSurface)
                .padding(.top, 16)
            content
                .padding(.top, 16)
            HStack(spacing: 4) {
                Spacer(minLength: 0)
                Button(action: onDismiss) {
                    Text("Cancel")
                        .font(JuiceFont.body(14, weight: .medium))
                        .foregroundStyle(palette.primary)
                        .padding(.horizontal, 12)
                        .frame(height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                if showConfirm {
                    Button(action: onConfirm) {
                        Text(confirmLabel)
                            .font(JuiceFont.body(14, weight: .medium))
                            .foregroundStyle(destructive ? palette.error : palette.primary)
                            .padding(.horizontal, 12)
                            .frame(height: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .disabled(!confirmEnabled)
                    .opacity(confirmEnabled ? 1 : 0.5)
                }
            }
            .padding(.top, 20)
        }
        .padding(24)
        .frame(maxWidth: 320, alignment: .leading)
        .background(
            palette.surfaceContainerHigh,
            in: RoundedRectangle(cornerRadius: 28, style: .continuous)
        )
        .padding(.horizontal, 24)
        .scaleEffect(shown ? 1 : 0.94)
        .opacity(shown ? 1 : 0)
        .onAppear {
            withAnimation(.spring(duration: 0.3)) { shown = true }
        }
    }
}

struct SettingsDialogOverlay<Content: View>: View {
    let palette: JuicePalette
    let onDismiss: () -> Void
    @ViewBuilder var content: Content

    var body: some View {
        ZStack {
            Color.black.opacity(0.35)
                .ignoresSafeArea()
                .onTapGesture(perform: onDismiss)
            content
        }
    }
}
