import SwiftUI

/// The same two faces the Compose app ships: Roboto for text, I pixel u for
/// the wordmark. The bundled Roboto only has a regular cut, so heavier
/// weights are synthesised, exactly as Compose does with weight 500/600.
enum JuiceFont {
    static func body(_ size: CGFloat, weight: Font.Weight = .regular) -> Font {
        .custom("Roboto-Regular", size: size).weight(weight)
    }

    static func wordmark(_ size: CGFloat) -> Font {
        .custom("I-pixel-u", size: size)
    }

    static func mono(_ size: CGFloat, weight: Font.Weight = .regular) -> Font {
        .system(size: size, weight: weight, design: .monospaced)
    }
}

/// "JUICEBOX²" in the pixel face with the box icon's black keyline.
struct TitleWordmark: View {
    var size: CGFloat = 28
    var color: Color

    private static let text = "JUICEBOX\u{00B2}"

    var body: some View {
        let outline = max(1, size * 0.055)
        ZStack {
            Text(Self.text)
                .font(JuiceFont.wordmark(size))
                .foregroundStyle(.black)
                .shadow(color: .black, radius: 0, x: outline, y: 0)
                .shadow(color: .black, radius: 0, x: -outline, y: 0)
                .shadow(color: .black, radius: 0, x: 0, y: outline)
                .shadow(color: .black, radius: 0, x: 0, y: -outline)
                .shadow(color: .black, radius: 0, x: outline, y: outline)
                .shadow(color: .black, radius: 0, x: -outline, y: outline)
                .shadow(color: .black, radius: 0, x: outline, y: -outline)
                .shadow(color: .black, radius: 0, x: -outline, y: -outline)
            Text(Self.text)
                .font(JuiceFont.wordmark(size))
                .foregroundStyle(color)
        }
        .lineLimit(1)
        .minimumScaleFactor(0.6)
        .fixedSize(horizontal: false, vertical: true)
    }
}

/// Android's `formatTtl`: the full label shown next to the retention slider.
enum TtlFormat {
    static func label(minutes: Int32) -> String {
        let hours = Double(minutes) / 60
        if hours < 1 { return "\(Int((hours * 60).rounded())) min" }
        if hours == 1 { return "1 hr" }
        if hours < 24 { return "\(Int(hours)) hr" }
        if hours == 24 { return "1 day" }
        if hours.truncatingRemainder(dividingBy: 24) == 0 { return "\(Int(hours / 24)) days" }
        return "\(Int(hours)) hr"
    }
}
