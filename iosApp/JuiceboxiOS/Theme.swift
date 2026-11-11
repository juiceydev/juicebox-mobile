import SwiftUI

extension Color {
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: 1
        )
    }
}

/// The Material 3 roles the Compose app uses, so both apps share one palette.
struct JuicePalette {
    let primary: Color
    let onPrimary: Color
    let primaryContainer: Color
    let onPrimaryContainer: Color
    let background: Color
    let surface: Color
    let surfaceContainerLowest: Color
    let surfaceContainerLow: Color
    let surfaceContainer: Color
    let surfaceContainerHigh: Color
    let surfaceContainerHighest: Color
    let onSurface: Color
    let onSurfaceVariant: Color
    let outline: Color
    let outlineVariant: Color
    /// Material's tertiary role, used for success accents and the session dot.
    let success: Color
    let error: Color
    let errorContainer: Color
    let onErrorContainer: Color
}

enum JuiceTheme {
    struct Mode: Identifiable {
        let id: String
        let label: String
    }

    static let modes: [Mode] = [
        Mode(id: "juicebox", label: "Juicebox"),
        Mode(id: "violet", label: "Violet"),
        Mode(id: "ocean", label: "Ocean"),
        Mode(id: "forest", label: "Forest"),
        Mode(id: "rose", label: "Rose"),
    ]

    static func label(for mode: String) -> String {
        modes.first { $0.id == mode }?.label ?? "Juicebox"
    }

    /// Material You is Android-only, so an exported "you" falls back to brand.
    static func normalized(_ mode: String) -> String {
        modes.contains { $0.id == mode } ? mode : "juicebox"
    }

    static func palette(mode: String, dark: Bool) -> JuicePalette {
        switch normalized(mode) {
        case "violet": return dark ? violetDark : violetLight
        case "ocean": return dark ? oceanDark : oceanLight
        case "forest": return dark ? forestDark : forestLight
        case "rose": return dark ? roseDark : roseLight
        default: return dark ? juiceboxDark : juiceboxLight
        }
    }

    static let juiceboxLight = JuicePalette(
        primary: Color(hex: 0xEA580C),
        onPrimary: Color(hex: 0xFFF7ED),
        primaryContainer: Color(hex: 0xFFEDD5),
        onPrimaryContainer: Color(hex: 0x7C2D12),
        background: Color(hex: 0xF5EDEA),
        surface: Color(hex: 0xF5EDEA),
        surfaceContainerLowest: Color(hex: 0xFFFDFC),
        surfaceContainerLow: Color(hex: 0xFAF4F1),
        surfaceContainer: Color(hex: 0xF5EDEA),
        surfaceContainerHigh: Color(hex: 0xEFE5DF),
        surfaceContainerHighest: Color(hex: 0xE5DBD2),
        onSurface: Color(hex: 0x1A1613),
        onSurfaceVariant: Color(hex: 0x54453C),
        outline: Color(hex: 0xA29188),
        outlineVariant: Color(hex: 0xD5C7BC),
        success: Color(hex: 0x16A34A),
        error: Color(hex: 0xBA1A1A),
        errorContainer: Color(hex: 0xFFDAD6),
        onErrorContainer: Color(hex: 0x410002)
    )

    static let juiceboxDark = JuicePalette(
        primary: Color(hex: 0xF97316),
        onPrimary: Color(hex: 0x1A1613),
        primaryContainer: Color(hex: 0x54453C),
        onPrimaryContainer: Color(hex: 0xF5EDEA),
        background: Color(hex: 0x0A0908),
        surface: Color(hex: 0x0A0908),
        surfaceContainerLowest: Color(hex: 0x0A0908),
        surfaceContainerLow: Color(hex: 0x1A1613),
        surfaceContainer: Color(hex: 0x2A211D),
        surfaceContainerHigh: Color(hex: 0x40342D),
        surfaceContainerHighest: Color(hex: 0x54453C),
        onSurface: Color(hex: 0xF5EDEA),
        onSurfaceVariant: Color(hex: 0xA29188),
        outline: Color(hex: 0x726057),
        outlineVariant: Color(hex: 0x40342D),
        success: Color(hex: 0x22C55E),
        error: Color(hex: 0xF87171),
        errorContainer: Color(hex: 0x7F1D1D),
        onErrorContainer: Color(hex: 0xFEE2E2)
    )

    static let violetLight = JuicePalette(
        primary: Color(hex: 0x65558F),
        onPrimary: Color(hex: 0xFFFFFF),
        primaryContainer: Color(hex: 0xE9DDFF),
        onPrimaryContainer: Color(hex: 0x4D3D75),
        background: Color(hex: 0xFDF7FF),
        surface: Color(hex: 0xFDF7FF),
        surfaceContainerLowest: Color(hex: 0xFFFFFF),
        surfaceContainerLow: Color(hex: 0xF8F2FA),
        surfaceContainer: Color(hex: 0xF2ECF4),
        surfaceContainerHigh: Color(hex: 0xECE6EE),
        surfaceContainerHighest: Color(hex: 0xE6E0E9),
        onSurface: Color(hex: 0x1D1B20),
        onSurfaceVariant: Color(hex: 0x49454E),
        outline: Color(hex: 0x7A757F),
        outlineVariant: Color(hex: 0xCAC4CF),
        success: Color(hex: 0x7E5260),
        error: Color(hex: 0xBA1A1A),
        errorContainer: Color(hex: 0xFFDAD6),
        onErrorContainer: Color(hex: 0x93000A)
    )

    static let violetDark = JuicePalette(
        primary: Color(hex: 0xCFBDFE),
        onPrimary: Color(hex: 0x36275D),
        primaryContainer: Color(hex: 0x4D3D75),
        onPrimaryContainer: Color(hex: 0xE9DDFF),
        background: Color(hex: 0x141218),
        surface: Color(hex: 0x141218),
        surfaceContainerLowest: Color(hex: 0x0F0D13),
        surfaceContainerLow: Color(hex: 0x1D1B20),
        surfaceContainer: Color(hex: 0x211F24),
        surfaceContainerHigh: Color(hex: 0x2B292F),
        surfaceContainerHighest: Color(hex: 0x36343A),
        onSurface: Color(hex: 0xE6E0E9),
        onSurfaceVariant: Color(hex: 0xCAC4CF),
        outline: Color(hex: 0x948F99),
        outlineVariant: Color(hex: 0x49454E),
        success: Color(hex: 0xEFB8C8),
        error: Color(hex: 0xFFB4AB),
        errorContainer: Color(hex: 0x93000A),
        onErrorContainer: Color(hex: 0xFFDAD6)
    )

    static let oceanLight = JuicePalette(
        primary: Color(hex: 0x2F628C),
        onPrimary: Color(hex: 0xFFFFFF),
        primaryContainer: Color(hex: 0xCEE5FF),
        onPrimaryContainer: Color(hex: 0x0E4A73),
        background: Color(hex: 0xF7F9FF),
        surface: Color(hex: 0xF7F9FF),
        surfaceContainerLowest: Color(hex: 0xFFFFFF),
        surfaceContainerLow: Color(hex: 0xF1F3F9),
        surfaceContainer: Color(hex: 0xECEEF3),
        surfaceContainerHigh: Color(hex: 0xE6E8EE),
        surfaceContainerHighest: Color(hex: 0xE0E2E8),
        onSurface: Color(hex: 0x181C20),
        onSurfaceVariant: Color(hex: 0x42474E),
        outline: Color(hex: 0x72777F),
        outlineVariant: Color(hex: 0xC2C7CF),
        success: Color(hex: 0x68587A),
        error: Color(hex: 0xBA1A1A),
        errorContainer: Color(hex: 0xFFDAD6),
        onErrorContainer: Color(hex: 0x93000A)
    )

    static let oceanDark = JuicePalette(
        primary: Color(hex: 0x9BCBFB),
        onPrimary: Color(hex: 0x003353),
        primaryContainer: Color(hex: 0x0E4A73),
        onPrimaryContainer: Color(hex: 0xCEE5FF),
        background: Color(hex: 0x101418),
        surface: Color(hex: 0x101418),
        surfaceContainerLowest: Color(hex: 0x0B0F12),
        surfaceContainerLow: Color(hex: 0x181C20),
        surfaceContainer: Color(hex: 0x1C2024),
        surfaceContainerHigh: Color(hex: 0x272A2F),
        surfaceContainerHighest: Color(hex: 0x323539),
        onSurface: Color(hex: 0xE0E2E8),
        onSurfaceVariant: Color(hex: 0xC2C7CF),
        outline: Color(hex: 0x8C9198),
        outlineVariant: Color(hex: 0x42474E),
        success: Color(hex: 0xD3BFE6),
        error: Color(hex: 0xFFB4AB),
        errorContainer: Color(hex: 0x93000A),
        onErrorContainer: Color(hex: 0xFFDAD6)
    )

    static let forestLight = JuicePalette(
        primary: Color(hex: 0x446732),
        onPrimary: Color(hex: 0xFFFFFF),
        primaryContainer: Color(hex: 0xC5EFAB),
        onPrimaryContainer: Color(hex: 0x2D4F1C),
        background: Color(hex: 0xF8FAF0),
        surface: Color(hex: 0xF8FAF0),
        surfaceContainerLowest: Color(hex: 0xFFFFFF),
        surfaceContainerLow: Color(hex: 0xF2F5EA),
        surfaceContainer: Color(hex: 0xEDEFE5),
        surfaceContainerHigh: Color(hex: 0xE7E9DF),
        surfaceContainerHighest: Color(hex: 0xE1E4D9),
        onSurface: Color(hex: 0x191D16),
        onSurfaceVariant: Color(hex: 0x43483E),
        outline: Color(hex: 0x74796D),
        outlineVariant: Color(hex: 0xC3C8BB),
        success: Color(hex: 0x386666),
        error: Color(hex: 0xBA1A1A),
        errorContainer: Color(hex: 0xFFDAD6),
        onErrorContainer: Color(hex: 0x93000A)
    )

    static let forestDark = JuicePalette(
        primary: Color(hex: 0xA9D291),
        onPrimary: Color(hex: 0x173807),
        primaryContainer: Color(hex: 0x2D4F1C),
        onPrimaryContainer: Color(hex: 0xC5EFAB),
        background: Color(hex: 0x11140F),
        surface: Color(hex: 0x11140F),
        surfaceContainerLowest: Color(hex: 0x0C0F0A),
        surfaceContainerLow: Color(hex: 0x191D16),
        surfaceContainer: Color(hex: 0x1D211A),
        surfaceContainerHigh: Color(hex: 0x282B24),
        surfaceContainerHighest: Color(hex: 0x32362F),
        onSurface: Color(hex: 0xE1E4D9),
        onSurfaceVariant: Color(hex: 0xC3C8BB),
        outline: Color(hex: 0x8D9286),
        outlineVariant: Color(hex: 0x43483E),
        success: Color(hex: 0xA0CFD0),
        error: Color(hex: 0xFFB4AB),
        errorContainer: Color(hex: 0x93000A),
        onErrorContainer: Color(hex: 0xFFDAD6)
    )

    static let roseLight = JuicePalette(
        primary: Color(hex: 0x8C4A60),
        onPrimary: Color(hex: 0xFFFFFF),
        primaryContainer: Color(hex: 0xFFD9E2),
        onPrimaryContainer: Color(hex: 0x703349),
        background: Color(hex: 0xFFF8F8),
        surface: Color(hex: 0xFFF8F8),
        surfaceContainerLowest: Color(hex: 0xFFFFFF),
        surfaceContainerLow: Color(hex: 0xFFF0F2),
        surfaceContainer: Color(hex: 0xFAEAED),
        surfaceContainerHigh: Color(hex: 0xF5E4E7),
        surfaceContainerHighest: Color(hex: 0xEFDFE1),
        onSurface: Color(hex: 0x22191C),
        onSurfaceVariant: Color(hex: 0x514347),
        outline: Color(hex: 0x837377),
        outlineVariant: Color(hex: 0xD5C2C6),
        success: Color(hex: 0x7C5635),
        error: Color(hex: 0xBA1A1A),
        errorContainer: Color(hex: 0xFFDAD6),
        onErrorContainer: Color(hex: 0x93000A)
    )

    static let roseDark = JuicePalette(
        primary: Color(hex: 0xFFB0C8),
        onPrimary: Color(hex: 0x541D32),
        primaryContainer: Color(hex: 0x703349),
        onPrimaryContainer: Color(hex: 0xFFD9E2),
        background: Color(hex: 0x191113),
        surface: Color(hex: 0x191113),
        surfaceContainerLowest: Color(hex: 0x140C0E),
        surfaceContainerLow: Color(hex: 0x22191C),
        surfaceContainer: Color(hex: 0x261D20),
        surfaceContainerHigh: Color(hex: 0x31282A),
        surfaceContainerHighest: Color(hex: 0x3C3235),
        onSurface: Color(hex: 0xEFDFE1),
        onSurfaceVariant: Color(hex: 0xD5C2C6),
        outline: Color(hex: 0x9E8C90),
        outlineVariant: Color(hex: 0x514347),
        success: Color(hex: 0xEFBD94),
        error: Color(hex: 0xFFB4AB),
        errorContainer: Color(hex: 0x93000A),
        onErrorContainer: Color(hex: 0xFFDAD6)
    )
}
