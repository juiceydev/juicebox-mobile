import SwiftUI

struct RootView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.colorScheme) private var scheme

    @State private var tab: AppTab = .upload

    var body: some View {
        let palette = model.palette(scheme)
        Group {
            switch tab {
            case .upload: UploadView()
            case .files: FilesView()
            case .settings: SettingsView()
            }
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            JuiceTabBar(selection: $tab, palette: palette) { _ in
                model.haptic()
            }
        }
        .background(palette.background.ignoresSafeArea())
        .tint(palette.primary)
        .environment(\.juiceGlass, model.settings.liquidGlass)
        .overlay(alignment: .top) {
            if let toast = model.toast {
                ToastView(message: toast, palette: palette)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .zIndex(2)
            }
        }
        .animation(.easeInOut(duration: 0.2), value: model.toast)
    }
}
