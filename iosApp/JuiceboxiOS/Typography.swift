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
/// is stroked behind a separate fill pass, so it stays mitred and square
/// without eating into the width of the letters.
struct TitleWordmark: UIViewRepresentable {
    var size: CGFloat = 28
    var color: Color

    private static let text = "JUICEBOX\u{00B2}"

    func makeUIView(context: Context) -> WordmarkView {
        WordmarkView()
    }

    func updateUIView(_ view: WordmarkView, context: Context) {
        view.configure(text: Self.text, size: size, color: UIColor(color))
    }

    func sizeThatFits(
        _ proposal: ProposedViewSize,
        uiView: WordmarkView,
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

/// A stroke-only label behind an identical filled label. The fill covers the
/// inner half of the stroke, leaving a sharp outside keyline and the original
/// letterform width untouched.
final class WordmarkView: UIView {
    private let outlineLabel = UILabel()
    private let fillLabel = UILabel()

    override init(frame: CGRect) {
        super.init(frame: frame)
        clipsToBounds = false
        for label in [outlineLabel, fillLabel] {
            label.numberOfLines = 1
            label.adjustsFontSizeToFitWidth = true
            label.minimumScaleFactor = 0.6
            label.lineBreakMode = .byClipping
            label.backgroundColor = .clear
            label.isUserInteractionEnabled = false
            addSubview(label)
        }
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        outlineLabel.frame = bounds
        fillLabel.frame = bounds
    }

    func configure(text: String, size: CGFloat, color: UIColor) {
        let font = UIFont(name: "I-pixel-u", size: size)
            ?? UIFont.systemFont(ofSize: size, weight: .bold)
        let paragraph = NSMutableParagraphStyle()
        paragraph.lineBreakMode = .byClipping

        outlineLabel.attributedText = NSAttributedString(
            string: text,
            attributes: [
                .font: font,
                .strokeColor: UIColor.black,
                .strokeWidth: 18,
                .paragraphStyle: paragraph,
            ]
        )
        fillLabel.attributedText = NSAttributedString(
            string: text,
            attributes: [
                .font: font,
                .foregroundColor: color,
                .paragraphStyle: paragraph,
            ]
        )
        invalidateIntrinsicContentSize()
    }

    override var intrinsicContentSize: CGSize {
        guard let text = fillLabel.attributedText, text.length > 0 else {
            return .zero
        }
        let measured = text.size()
        return CGSize(width: ceil(measured.width), height: ceil(measured.height))
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
