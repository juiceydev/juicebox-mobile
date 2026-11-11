import Foundation

enum JuiceFormat {

    static func bytes(_ value: Int64) -> String {
        guard value > 0 else { return "0 B" }
        let units = ["B", "KB", "MB", "GB", "TB"]
        var size = Double(value)
        var index = 0
        while size >= 1024 && index < units.count - 1 {
            size /= 1024
            index += 1
        }
        if index == 0 { return "\(Int(size)) B" }
        let rounded = (size * 10).rounded() / 10
        let text = rounded == rounded.rounded()
            ? String(format: "%.0f", rounded)
            : String(format: "%.1f", rounded)
        return "\(text) \(units[index])"
    }

    static func timeLeft(expiresAt: Int64, now: Date = Date()) -> String {
        let seconds = expiresAt - Int64(now.timeIntervalSince1970)
        if expiresAt <= 0 { return "No expiry" }
        if seconds <= 0 { return "Expired" }
        let hours = seconds / 3600
        let minutes = (seconds % 3600) / 60
        if hours >= 24 {
            let days = hours / 24
            let rest = hours % 24
            return rest > 0 ? "\(days)d \(rest)h left" : "\(days)d left"
        }
        if hours >= 1 {
            return minutes > 0 ? "\(hours)h \(minutes)m left" : "\(hours)h left"
        }
        return "\(max(1, minutes))m left"
    }

    static func expiryDate(_ epoch: Int64) -> String {
        guard epoch > 0 else { return "No expiry" }
        let date = Date(timeIntervalSince1970: TimeInterval(epoch))
        let formatter = DateFormatter()
        formatter.locale = Locale.current
        formatter.dateFormat = "d MMM 'at' HH:mm"
        return formatter.string(from: date)
    }

    static func expiryFraction(uploadedAt: Int64, expiresAt: Int64, now: Date = Date()) -> Double {
        guard expiresAt > 0 else { return 0 }
        let total = Double(max(1, expiresAt - uploadedAt))
        let left = Double(expiresAt) - now.timeIntervalSince1970
        return min(1, max(0, left / total))
    }

    static func mimeSymbol(_ mime: String) -> String {
        if mime.hasPrefix("image/") { return "photo" }
        if mime.hasPrefix("video/") { return "film" }
        if mime.hasPrefix("audio/") { return "waveform" }
        if mime.hasPrefix("text/") { return "doc.text" }
        if mime.contains("pdf") { return "doc.richtext" }
        if mime.contains("zip") || mime.contains("archive") || mime.contains("tar") {
            return "archivebox"
        }
        if mime.contains("json") || mime.contains("javascript") || mime.contains("xml") {
            return "chevron.left.forwardslash.chevron.right"
        }
        return "doc"
    }
}
