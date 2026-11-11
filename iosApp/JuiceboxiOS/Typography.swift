import SwiftUI
import UIKit

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

/// "JUICEBOX²" in the pixel face with the box icon's black keyline. The keyline
/// is a Core Text stroke, not a stack of shadows, so its corners stay mitred
/// and square like the Compose `Stroke` on Android.
struct TitleWordmark: UIViewRepresentable {
    var size: CGFloat = 28
    var color: Color

    private static let text = "JUICEBOX\u{00B2}"

    func makeUIView(context: Context) -> UILabel {
        let label = UILabel()
        label.numberOfLines = 1
        label.adjustsFontSizeToFitWidth = true
        label.minimumScaleFactor = 0.6
        label.lineBreakMode = .byClipping
        label.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        label.setContentHuggingPriority(.defaultLow, for: .horizontal)
        return label
    }

    func updateUIView(_ label: UILabel, context: Context) {
        let font = UIFont(name: "I-pixel-u", size: size)
            ?? UIFont.systemFont(ofSize: size, weight: .bold)
        let paragraph = NSMutableParagraphStyle()
        paragraph.lineBreakMode = .byClipping
        label.attributedText = NSAttributedString(
            string: Self.text,
            attributes: [
                .font: font,
                .foregroundColor: UIColor(color),
                .strokeColor: UIColor.black,
                .strokeWidth: -9,
                .paragraphStyle: paragraph,
            ]
        )
    }

    func sizeThatFits(
        _ proposal: ProposedViewSize,
        uiView: UILabel,
        context: Context
    ) -> CGSize? {
        let intrinsic = uiView.intrinsicContentSize
        guard let width = proposal.width,
              width > 0,
              width < intrinsic.width else {
            return intrinsic
        }
        // Narrow rows shrink the wordmark instead of clipping it, matching the
        // old `minimumScaleFactor` behaviour.
        return CGSize(width: width, height: intrinsic.height)
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
