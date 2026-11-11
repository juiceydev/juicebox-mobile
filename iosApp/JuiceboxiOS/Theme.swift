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

struct JuicePalette {
    let primary: Color
    let onPrimary: Color
    let background: Color
    let surface: Color
    let surfaceContainer: Color
    let surfaceContainerHigh: Color
    let onSurface: Color
    let onSurfaceVariant: Color
    let outline: Color
    let success: Color
    let error: Color
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
        background: Color(hex: 0xF5EDEA),
        surface: Color(hex: 0xF5EDEA),
        surfaceContainer: Color(hex: 0xF5EDEA),
        surfaceContainerHigh: Color(hex: 0xEFE5DF),
        onSurface: Color(hex: 0x1A1613),
        onSurfaceVariant: Color(hex: 0x54453C),
        outline: Color(hex: 0xA29188),
        success: Color(hex: 0x16A34A),
        error: Color(hex: 0xBA1A1A)
    )

    static let juiceboxDark = JuicePalette(
        primary: Color(hex: 0xF97316),
        onPrimary: Color(hex: 0x1A1613),
        background: Color(hex: 0x0A0908),
        surface: Color(hex: 0x0A0908),
        surfaceContainer: Color(hex: 0x2A211D),
        surfaceContainerHigh: Color(hex: 0x40342D),
        onSurface: Color(hex: 0xF5EDEA),
        onSurfaceVariant: Color(hex: 0xA29188),
        outline: Color(hex: 0x726057),
        success: Color(hex: 0x22C55E),
        error: Color(hex: 0xF87171)
    )

    static let violetLight = JuicePalette(
        primary: Color(hex: 0x65558F),
        onPrimary: .white,
        background: Color(hex: 0xFDF7FF),
        surface: Color(hex: 0xFDF7FF),
        surfaceContainer: Color(hex: 0xF2ECF4),
        surfaceContainerHigh: Color(hex: 0xECE6EE),
        onSurface: Color(hex: 0x1D1B20),
        onSurfaceVariant: Color(hex: 0x49454E),
        outline: Color(hex: 0x7A757F),
        success: Color(hex: 0x16A34A),
        error: Color(hex: 0xBA1A1A)
    )

    static let violetDark = JuicePalette(
        primary: Color(hex: 0xCFBDFE),
        onPrimary: Color(hex: 0x36275D),
        background: Color(hex: 0x141218),
        surface: Color(hex: 0x141218),
        surfaceContainer: Color(hex: 0x211F24),
        surfaceContainerHigh: Color(hex: 0x2B292F),
        onSurface: Color(hex: 0xE6E0E9),
        onSurfaceVariant: Color(hex: 0xCAC4CF),
        outline: Color(hex: 0x948F99),
        success: Color(hex: 0x22C55E),
        error: Color(hex: 0xF87171)
    )

    static let oceanLight = JuicePalette(
        primary: Color(hex: 0x2F628C),
        onPrimary: .white,
        background: Color(hex: 0xF7F9FF),
        surface: Color(hex: 0xF7F9FF),
        surfaceContainer: Color(hex: 0xECEEF3),
        surfaceContainerHigh: Color(hex: 0xE6E8EE),
        onSurface: Color(hex: 0x181C20),
        onSurfaceVariant: Color(hex: 0x42474E),
        outline: Color(hex: 0x72777F),
        success: Color(hex: 0x16A34A),
        error: Color(hex: 0xBA1A1A)
    )

    static let oceanDark = JuicePalette(
        primary: Color(hex: 0x9BCBFB),
        onPrimary: Color(hex: 0x003353),
        background: Color(hex: 0x101418),
        surface: Color(hex: 0x101418),
        surfaceContainer: Color(hex: 0x1C2024),
        surfaceContainerHigh: Color(hex: 0x272A2F),
        onSurface: Color(hex: 0xE0E2E8),
        onSurfaceVariant: Color(hex: 0xC2C7CF),
        outline: Color(hex: 0x8C9198),
        success: Color(hex: 0x22C55E),
        error: Color(hex: 0xF87171)
    )

    static let forestLight = JuicePalette(
        primary: Color(hex: 0x446732),
        onPrimary: .white,
        background: Color(hex: 0xF8FAF0),
        surface: Color(hex: 0xF8FAF0),
        surfaceContainer: Color(hex: 0xEDEFE5),
        surfaceContainerHigh: Color(hex: 0xE7E9DF),
        onSurface: Color(hex: 0x191D16),
        onSurfaceVariant: Color(hex: 0x43483E),
        outline: Color(hex: 0x74796D),
        success: Color(hex: 0x16A34A),
        error: Color(hex: 0xBA1A1A)
    )

    static let forestDark = JuicePalette(
        primary: Color(hex: 0xA9D291),
        onPrimary: Color(hex: 0x173807),
        background: Color(hex: 0x11140F),
        surface: Color(hex: 0x11140F),
        surfaceContainer: Color(hex: 0x1D211A),
        surfaceContainerHigh: Color(hex: 0x282B24),
        onSurface: Color(hex: 0xE1E4D9),
        onSurfaceVariant: Color(hex: 0xC3C8BB),
        outline: Color(hex: 0x8D9286),
        success: Color(hex: 0x22C55E),
        error: Color(hex: 0xF87171)
    )

    static let roseLight = JuicePalette(
        primary: Color(hex: 0x8C4A60),
        onPrimary: .white,
        background: Color(hex: 0xFFF8F8),
        surface: Color(hex: 0xFFF8F8),
        surfaceContainer: Color(hex: 0xFAEAED),
        surfaceContainerHigh: Color(hex: 0xF5E4E7),
        onSurface: Color(hex: 0x22191C),
        onSurfaceVariant: Color(hex: 0x514347),
        outline: Color(hex: 0x837377),
        success: Color(hex: 0x16A34A),
        error: Color(hex: 0xBA1A1A)
    )

    static let roseDark = JuicePalette(
        primary: Color(hex: 0xFFB0C8),
        onPrimary: Color(hex: 0x541D32),
        background: Color(hex: 0x191113),
        surface: Color(hex: 0x191113),
        surfaceContainer: Color(hex: 0x261D20),
        surfaceContainerHigh: Color(hex: 0x31282A),
        onSurface: Color(hex: 0xEFDFE1),
        onSurfaceVariant: Color(hex: 0xD5C2C6),
        outline: Color(hex: 0x9E8C90),
        success: Color(hex: 0x22C55E),
        error: Color(hex: 0xF87171)
    )
}
