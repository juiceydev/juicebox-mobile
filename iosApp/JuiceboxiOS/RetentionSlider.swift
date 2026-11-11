import JuiceboxCore
import SwiftUI
import UIKit

/// Android's Material 3 retention slider: a 4pt track with a bar thumb, a dot
/// per allowed stop and compact labels pinned to each stop's fraction.
struct RetentionSlider: View {
    let options: [IosTtlOption]
    let minutes: Int32
    let palette: JuicePalette
    let haptics: Bool
    let onSelect: (Int32) -> Void

    private var index: Int {
        if let exact = options.firstIndex(where: { $0.minutes == minutes }) {
            return exact
        }
        return options.indices.min(by: {
            abs(options[$0].minutes - minutes) < abs(options[$1].minutes - minutes)
        }) ?? 0
    }

    var body: some View {
        if options.isEmpty {
            EmptyView()
        } else {
            VStack(alignment: .leading, spacing: 2) {
                HStack(alignment: .firstTextBaseline) {
                    Text("Retention")
                        .font(JuiceFont.body(14, weight: .medium))
                        .foregroundStyle(palette.onSurfaceVariant)
                    Spacer(minLength: 8)
                    Text(TtlFormat.label(minutes: options[index].minutes))
                        .font(JuiceFont.body(16, weight: .medium))
                        .foregroundStyle(palette.primary)
                }
                if options.count > 1 {
                    track
                } else {
                    Color.clear.frame(height: 4)
                }
                labels
            }
        }
    }

    private var track: some View {
        GeometryReader { geo in
            let width = geo.size.width
            let inset: CGFloat = 10
            let usable = max(1, width - inset * 2)
            let last = max(1, options.count - 1)
            let fraction = CGFloat(index) / CGFloat(last)
            let thumbX = inset + usable * fraction
            ZStack(alignment: .topLeading) {
                Capsule()
                    .fill(palette.surfaceContainerHighest)
                    .frame(width: usable, height: 4)
                    .offset(x: inset, y: 20)
                Capsule()
                    .fill(palette.primary)
                    .frame(width: max(2, usable * fraction), height: 4)
                    .offset(x: inset, y: 20)
                ForEach(0..<options.count, id: \.self) { stop in
                    let f = CGFloat(stop) / CGFloat(last)
                    Circle()
                        .fill(
                            stop <= index
                                ? palette.onPrimary
                                : palette.onSurfaceVariant.opacity(0.6)
                        )
                        .frame(width: 3, height: 3)
                        .offset(x: inset + usable * f - 1.5, y: 20.5)
                }
                RoundedRectangle(cornerRadius: 2, style: .continuous)
                    .fill(palette.primary)
                    .frame(width: 4, height: 44)
                    .offset(x: thumbX - 2, y: 0)
            }
            .frame(height: 44)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        let clamped = min(max(value.location.x, inset), width - inset)
                        let raw = (clamped - inset) / usable
                        let next = Int((raw * CGFloat(last)).rounded())
                        guard next != index else { return }
                        if haptics {
                            UISelectionFeedbackGenerator().selectionChanged()
                        }
                        onSelect(options[next].minutes)
                    }
            )
        }
        .frame(height: 44)
    }

    private var labels: some View {
        GeometryReader { geo in
            let width = geo.size.width
            let inset: CGFloat = 2
            let usable = max(1, width - inset * 2)
            let last = max(1, options.count - 1)
            ForEach(0..<options.count, id: \.self) { stop in
                let f = options.count == 1 ? 0.5 : CGFloat(stop) / CGFloat(last)
                Text(options[stop].label)
                    .font(JuiceFont.body(10))
                    .foregroundStyle(
                        stop == index
                            ? palette.primary
                            : palette.onSurfaceVariant.opacity(0.6)
                    )
                    .fixedSize()
                    .position(x: inset + usable * f, y: 7)
            }
        }
        .frame(height: 14)
    }
}
