import SwiftUI

struct RootView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.colorScheme) private var scheme

    @State private var tab: AppTab = .launchDefault

    var body: some View {
        let palette = model.palette(scheme)
        TabView(selection: $tab) {
            UploadView()
                .tag(AppTab.upload)
                .tabItem { Label("Upload", systemImage: "arrow.up.doc") }
            FilesView()
                .tag(AppTab.files)
                .tabItem { Label("Files", systemImage: "folder") }
            SettingsView()
                .tag(AppTab.settings)
                .tabItem { Label("Settings", systemImage: "slider.horizontal.3") }
        }
        .onChange(of: tab) { _, _ in
            model.haptic()
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
