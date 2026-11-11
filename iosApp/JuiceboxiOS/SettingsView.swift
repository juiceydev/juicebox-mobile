import JuiceboxCore
import SwiftUI
import UniformTypeIdentifiers

struct SettingsView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.colorScheme) private var scheme
    @Environment(\.openURL) private var openURL

    @State private var page: SettingsPage?
    @State private var dialog: SettingsDialog?

    @State private var serverInput = ""
    @State private var serverBusy = false
    @State private var customHost = ""

    @State private var passwordDraft = ""
    @State private var showPassword = false

    @State private var includeSession = false
    @State private var exportDocument = LibraryDocument(text: "")
    @State private var showExporter = false
    @State private var showImporter = false

    enum SettingsPage: String, Hashable {
        case server, storage, appearance, uploads, links, library, about, session

        var title: String {
            switch self {
            case .server: return "Server"
            case .storage: return "Storage"
            case .appearance: return "Appearance"
            case .uploads: return "Uploads"
            case .links: return "Default Link"
            case .library: return "Library"
            case .about: return "About"
            case .session: return "This session"
            }
        }
    }

    enum SettingsDialog: Hashable {
        case share, password, forget, clear
    }

    /// Screenshot runs can launch straight onto a subpage with
    /// SIMCTL_CHILD_JUICEBOX_START_PAGE.
    init() {
        let start = ProcessInfo.processInfo.environment["JUICEBOX_START_PAGE"]
            .flatMap { SettingsPage(rawValue: $0) }
        _page = State(initialValue: start)
    }

    var body: some View {
        let palette = model.palette(scheme)
        ZStack {
            palette.background.ignoresSafeArea()
            if let page {
                subpage(page, palette)
            } else {
                root(palette)
            }
            if let dialog {
                SettingsDialogOverlay(
                    palette: palette,
                    onDismiss: { self.dialog = nil },
                    content: { dialogCard(dialog, palette) }
                )
            }
        }
        .onAppear {
            serverInput = model.settings.serverUrl
        }
        .fileExporter(
            isPresented: $showExporter,
            document: exportDocument,
            contentType: .json,
            defaultFilename: "juicebox-library"
        ) { result in
            if case .failure = result { model.flash("Export cancelled") }
        }
        .fileImporter(isPresented: $showImporter, allowedContentTypes: [.json]) { result in
            handleImport(result)
        }
    }

    // MARK: - Root

    private func root(_ palette: JuicePalette) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Settings")
                        .font(JuiceFont.body(36))
                        .tracking(-0.8)
                        .foregroundStyle(palette.onSurface)
                    TitleWordmark(size: 16, color: palette.primary)
                }

                SettingsGroup(palette: palette) {
                    SettingsNavRow(icon: "globe", title: "Server", palette: palette) {
                        page = .server
                    }
                    HairlineDivider(palette: palette)
                    SettingsNavRow(icon: "cloud", title: "Storage", palette: palette) {
                        page = .storage
                    }
                    HairlineDivider(palette: palette)
                    SettingsNavRow(icon: "moon", title: "Appearance", palette: palette) {
                        page = .appearance
                    }
                    HairlineDivider(palette: palette)
                    SettingsNavRow(icon: "arrow.up.circle", title: "Uploads", palette: palette) {
                        page = .uploads
                    }
                    HairlineDivider(palette: palette)
                    SettingsNavRow(icon: "link", title: "Default Link", palette: palette) {
                        page = .links
                    }
                    HairlineDivider(palette: palette)
                    SettingsNavRow(icon: "folder", title: "Library", palette: palette) {
                        page = .library
                    }
                    HairlineDivider(palette: palette)
                    SettingsNavRow(icon: "info.circle", title: "About", palette: palette) {
                        page = .about
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)
            .padding(.bottom, 32)
        }
    }

    private func subpage(_ page: SettingsPage, _ palette: JuicePalette) -> some View {
        VStack(spacing: 0) {
            SettingsTopBar(title: page.title, palette: palette) {
                self.page = nil
            }
            ScrollView {
                pageContent(page, palette)
                    .padding(.horizontal, 20)
                    .padding(.bottom, 32)
            }
        }
    }

    @ViewBuilder
    private func pageContent(_ page: SettingsPage, _ palette: JuicePalette) -> some View {
        switch page {
        case .server: serverPage(palette)
        case .storage: storagePage(palette)
        case .appearance: appearancePage(palette)
        case .uploads: uploadsPage(palette)
        case .links: linksPage(palette)
        case .library: libraryPage(palette)
        case .about: aboutPage(palette)
        case .session: sessionPage(palette)
        }
    }

    // MARK: - Server

    private var connectionLabel: String {
        if model.connection.connecting { return "Checking" }
        if model.connection.connected {
            if let version = model.config?.version, !version.isEmpty {
                return "Connected - v\(version)"
            }
            return "Connected"
        }
        return "Not connected"
    }

    private func serverPage(_ palette: JuicePalette) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            SettingsGroup(palette: palette) {
                VStack(alignment: .leading, spacing: 12) {
                    SettingsField(
                        label: "Server URL",
                        placeholder: "https://box.juicey.dev",
                        text: $serverInput,
                        palette: palette,
                        labelBackground: palette.surfaceContainerLow,
                        keyboard: .URL
                    )
                    Text(connectionLabel)
                        .font(JuiceFont.body(12, weight: .medium))
                        .foregroundStyle(palette.onSurfaceVariant)
                    SettingsPrimaryButton(
                        title: "Save",
                        icon: "checkmark",
                        busy: serverBusy,
                        enabled: !serverInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                        palette: palette
                    ) {
                        saveServer()
                    }
                }
                .padding(18)
            }

            if let config = model.config {
                SettingsGroup(palette: palette) {
                    SettingsInfoRow(
                        title: "Largest file",
                        value: JuiceFormat.bytes(config.maxFileSizeBytes),
                        palette: palette
                    )
                    HairlineDivider(palette: palette)
                    SettingsInfoRow(
                        title: "Retention",
                        value: "up to \(TtlFormat.label(minutes: Int32((config.maxTtlHours * 60).rounded())))",
                        palette: palette
                    )
                }
            }
        }
    }

    private func saveServer() {
        Task {
            serverBusy = true
            _ = await model.applyServer(serverInput)
            serverBusy = false
            serverInput = model.settings.serverUrl
        }
    }

    // MARK: - Storage

    private var savedHosts: [String] {
        let official = Set(model.storageHosts.map(\.url))
        return model.settings.customHosts.filter { !official.contains($0) }
    }

    private func storagePage(_ palette: JuicePalette) -> some View {
        let selected = model.settings.storageHost
        return VStack(alignment: .leading, spacing: 16) {
            SettingsGroup(palette: palette) {
                ChoiceOption(label: "Auto", selected: selected == nil, palette: palette) {
                    model.setStorageHost(nil)
                }
                ForEach(model.storageHosts, id: \.url) { host in
                    HairlineDivider(palette: palette)
                    ChoiceOption(
                        label: host.name,
                        selected: selected == host.url,
                        palette: palette
                    ) {
                        model.setStorageHost(host.url)
                    }
                }
            }

            if !savedHosts.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    groupLabel("Saved nodes", palette)
                    SettingsGroup(palette: palette) {
                        ForEach(Array(savedHosts.enumerated()), id: \.offset) { index, url in
                            if index > 0 { HairlineDivider(palette: palette) }
                            ChoiceOption(
                                label: url
                                    .replacingOccurrences(of: "https://", with: "")
                                    .replacingOccurrences(of: "http://", with: "")
                                    .trimmingCharacters(in: CharacterSet(charactersIn: "/")),
                                selected: selected == url,
                                palette: palette
                            ) {
                                model.setStorageHost(url)
                            }
                        }
                    }
                }
            }

            SettingsGroup(palette: palette) {
                VStack(alignment: .leading, spacing: 12) {
                    SettingsField(
                        label: "Custom node",
                        placeholder: "https://f-eu3.juicey.dev",
                        text: $customHost,
                        palette: palette,
                        labelBackground: palette.surfaceContainerLow,
                        keyboard: .URL
                    )
                    HStack {
                        Spacer(minLength: 0)
                        SettingsTonalButton(title: "Use node", icon: "cloud", palette: palette) {
                            useCustomHost()
                        }
                    }
                }
                .padding(18)
            }
        }
    }

    private func useCustomHost() {
        var value = customHost.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { return }
        if !value.contains("://") { value = "https://" + value }
        while value.hasSuffix("/") { value.removeLast() }
        model.setStorageHost(value)
        model.flash("Storage node saved")
        customHost = ""
    }

    // MARK: - Appearance

    private func appearancePage(_ palette: JuicePalette) -> some View {
        let current = JuiceTheme.normalized(model.settings.themeMode)
        return VStack(alignment: .leading, spacing: 16) {
            SettingsGroup(palette: palette) {
                ForEach(Array(JuiceTheme.modes.enumerated()), id: \.element.id) { index, theme in
                    if index > 0 { HairlineDivider(palette: palette) }
                    ThemeChoiceRow(
                        theme: theme,
                        swatches: swatches(theme.id),
                        selected: current == theme.id,
                        palette: palette
                    ) {
                        model.setTheme(theme.id)
                    }
                }
            }

            if #available(iOS 26.0, *) {
                SettingsGroup(palette: palette) {
                    SettingsToggleRow(
                        title: "Liquid Glass",
                        body: "Translucent cards and tab bar (iOS 26).",
                        isOn: model.settings.liquidGlass,
                        palette: palette
                    ) {
                        model.setLiquidGlass($0)
                    }
                }
            }
        }
    }

    private func swatches(_ id: String) -> [Color] {
        switch id {
        case "violet":
            return [Color(hex: 0x6750A4), Color(hex: 0x7E5260), Color(hex: 0x386A20)]
        case "ocean":
            return [Color(hex: 0x2F628C), Color(hex: 0x68587A), Color(hex: 0x386666)]
        case "forest":
            return [Color(hex: 0x446732), Color(hex: 0x386666), Color(hex: 0x7C5635)]
        case "rose":
            return [Color(hex: 0x8C4A60), Color(hex: 0x7C5635), Color(hex: 0x386A20)]
        default:
            return [Color(hex: 0xF97316), Color(hex: 0xEA580C), Color(hex: 0x22C55E)]
        }
    }

    // MARK: - Uploads

    private var shareModeLabel: String {
        model.settings.shareLinkMode == "text" ? "Upload as text" : "Fetch video"
    }

    private func uploadsPage(_ palette: JuicePalette) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            SettingsGroup(palette: palette) {
                SettingsToggleRow(
                    title: "Quick link",
                    isOn: model.settings.quickLink,
                    palette: palette
                ) {
                    model.setQuickLink($0)
                }
                HairlineDivider(palette: palette)
                SettingsToggleRow(
                    title: "Copy link automatically",
                    isOn: model.settings.autoCopy,
                    palette: palette
                ) {
                    model.setAutoCopy($0)
                }
                HairlineDivider(palette: palette)
                SettingsToggleRow(
                    title: "Haptics",
                    isOn: model.settings.haptics,
                    palette: palette
                ) {
                    model.setHaptics($0)
                }
                HairlineDivider(palette: palette)
                SettingsChoiceRow(
                    title: "Shared links",
                    value: shareModeLabel,
                    palette: palette
                ) {
                    dialog = .share
                }
            }

            SettingsGroup(palette: palette) {
                SettingsToggleRow(
                    title: "Lock uploads",
                    isOn: model.settings.lockUploads,
                    palette: palette
                ) { checked in
                    if checked {
                        passwordDraft = model.settings.uploadPassword
                        showPassword = false
                        dialog = .password
                    } else {
                        model.setLockUploads(false)
                    }
                }
                if model.settings.lockUploads && !model.settings.uploadPassword.isEmpty {
                    HairlineDivider(palette: palette)
                    SettingsActionRow(
                        icon: "key",
                        title: "Change password",
                        palette: palette
                    ) {
                        passwordDraft = model.settings.uploadPassword
                        showPassword = false
                        dialog = .password
                    }
                }
            }
        }
    }

    // MARK: - Default link

    private func linksPage(_ palette: JuicePalette) -> some View {
        let variants = [("preview", "Preview"), ("raw", "Raw"), ("download", "Download")]
        return VStack(alignment: .leading, spacing: 16) {
            SettingsGroup(palette: palette) {
                ForEach(Array(variants.enumerated()), id: \.offset) { index, item in
                    if index > 0 { HairlineDivider(palette: palette) }
                    ChoiceOption(
                        label: item.1,
                        selected: model.settings.linkVariant == item.0,
                        palette: palette
                    ) {
                        model.setLinkVariant(item.0)
                    }
                }
            }
        }
    }

    // MARK: - Library

    private func libraryPage(_ palette: JuicePalette) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            SettingsGroup(palette: palette) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(model.libraryCount) files")
                        .font(JuiceFont.body(16, weight: .medium))
                        .foregroundStyle(palette.onSurface)
                    Text("On this device")
                        .font(JuiceFont.body(12))
                        .foregroundStyle(palette.onSurfaceVariant)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(18)
                HairlineDivider(palette: palette)
                SettingsToggleRow(
                    title: "Include identity",
                    isOn: includeSession,
                    palette: palette
                ) {
                    includeSession = $0
                }
                HairlineDivider(palette: palette)
                HStack(spacing: 8) {
                    SettingsTonalButton(title: "Export", icon: "arrow.down.circle", palette: palette) {
                        runExport()
                    }
                    SettingsTonalButton(title: "Import", icon: "arrow.up.circle", palette: palette) {
                        showImporter = true
                    }
                }
                .padding(12)
            }

            SettingsGroup(palette: palette) {
                SettingsActionRow(
                    icon: "trash",
                    title: "Clear local library",
                    destructive: true,
                    palette: palette
                ) {
                    dialog = .clear
                }
            }
        }
    }

    private func runExport() {
        Task {
            if let raw = await model.exportLibrary(includeSession: includeSession) {
                exportDocument = LibraryDocument(text: raw)
                showExporter = true
            }
        }
    }

    private func handleImport(_ result: Result<URL, Error>) {
        switch result {
        case .success(let url):
            let scoped = url.startAccessingSecurityScopedResource()
            defer {
                if scoped { url.stopAccessingSecurityScopedResource() }
            }
            do {
                let raw = try String(contentsOf: url, encoding: .utf8)
                Task {
                    _ = await model.importLibrary(raw)
                    await model.refreshLibrary()
                }
            } catch {
                model.flash("Couldn't read that file")
            }
        case .failure:
            model.flash("Import cancelled")
        }
    }

    // MARK: - About

    private func aboutPage(_ palette: JuicePalette) -> some View {
        let active = model.connection.connected
        return VStack(alignment: .leading, spacing: 16) {
            SettingsGroup(palette: palette) {
                HStack(spacing: 14) {
                    Image("Logo")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 52, height: 52)
                    VStack(alignment: .leading, spacing: 2) {
                        TitleWordmark(size: 22, color: palette.primary)
                        Text("for iOS - \(model.version)")
                            .font(JuiceFont.body(11))
                            .foregroundStyle(palette.onSurfaceVariant)
                    }
                    Spacer(minLength: 0)
                }
                .padding(18)
                HairlineDivider(palette: palette)
                SettingsNavRow(
                    icon: "iphone",
                    title: "This session",
                    value: active ? "Active" : "None",
                    palette: palette
                ) {
                    page = .session
                }
                HairlineDivider(palette: palette)
                SettingsActionRow(icon: "globe", title: "Open site", palette: palette) {
                    openSite()
                }
                HairlineDivider(palette: palette)
                SettingsActionRow(
                    icon: "iphone",
                    title: "Forget device",
                    destructive: true,
                    palette: palette
                ) {
                    dialog = .forget
                }
            }
        }
    }

    private func openSite() {
        if let url = URL(string: model.settings.serverUrl) {
            openURL(url)
        }
    }

    // MARK: - Session

    private func sessionPage(_ palette: JuicePalette) -> some View {
        let active = model.connection.connected
        let host = model.serverHost
        return VStack(alignment: .leading, spacing: 16) {
            SettingsGroup(palette: palette) {
                HStack(spacing: 14) {
                    ZStack {
                        Circle()
                            .fill(palette.primaryContainer)
                            .frame(width: 48, height: 48)
                        Image(systemName: "iphone")
                            .font(.system(size: 20, weight: .regular))
                            .foregroundStyle(palette.onPrimaryContainer)
                    }
                    VStack(alignment: .leading, spacing: 2) {
                        Text(active ? "Active session" : "No session yet")
                            .font(JuiceFont.body(16, weight: .medium))
                            .foregroundStyle(palette.onSurface)
                        Text(active ? "Anonymous identity saved on this phone" : "Connect to start one")
                            .font(JuiceFont.body(12))
                            .foregroundStyle(palette.onSurfaceVariant)
                    }
                    Spacer(minLength: 0)
                }
                .padding(18)
                HairlineDivider(palette: palette)
                SettingsInfoRow(title: "Server", value: host, palette: palette)
                HairlineDivider(palette: palette)
                SettingsInfoRow(title: "Library", value: "\(model.libraryCount) files", palette: palette)
                HairlineDivider(palette: palette)
                SettingsInfoRow(title: "App", value: "v\(model.version)", palette: palette)
            }

            Text(
                "This session is an anonymous cookie stored on this device. " +
                "It keeps your library across restarts; forgetting it starts a fresh identity " +
                "and leaves existing links online."
            )
            .font(JuiceFont.body(12))
            .foregroundStyle(palette.onSurfaceVariant)
            .padding(.horizontal, 6)

            SettingsGroup(palette: palette) {
                SettingsActionRow(
                    icon: "iphone",
                    title: "Forget device",
                    destructive: true,
                    palette: palette
                ) {
                    dialog = .forget
                }
            }
        }
    }

    // MARK: - Dialogs

    @ViewBuilder
    private func dialogCard(_ dialog: SettingsDialog, _ palette: JuicePalette) -> some View {
        switch dialog {
        case .share:
            SettingsDialogCard(
                icon: "link",
                title: "Shared links",
                palette: palette,
                confirmLabel: "Cancel",
                onConfirm: { self.dialog = nil },
                onDismiss: { self.dialog = nil }
            ) {
                VStack(spacing: 0) {
                    ChoiceOption(
                        label: "Fetch video",
                        selected: model.settings.shareLinkMode != "text",
                        palette: palette
                    ) {
                        model.setShareLinkMode("fetch")
                        self.dialog = nil
                    }
                    ChoiceOption(
                        label: "Upload as text",
                        selected: model.settings.shareLinkMode == "text",
                        palette: palette
                    ) {
                        model.setShareLinkMode("text")
                        self.dialog = nil
                    }
                }
            }
        case .password:
            SettingsDialogCard(
                icon: "key",
                title: "Upload password",
                palette: palette,
                confirmLabel: "Save",
                confirmEnabled: !passwordDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                onConfirm: {
                    let value = passwordDraft.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !value.isEmpty else { return }
                    model.setUploadPassword(value)
                    model.setLockUploads(true)
                    self.dialog = nil
                },
                onDismiss: { self.dialog = nil }
            ) {
                HStack(spacing: 8) {
                    SettingsField(
                        label: "Password",
                        text: $passwordDraft,
                        palette: palette,
                        labelBackground: palette.surfaceContainerHigh,
                        keyboard: .default,
                        secure: !showPassword
                    )
                    Button {
                        showPassword.toggle()
                    } label: {
                        Image(systemName: showPassword ? "eye.slash" : "eye")
                            .font(.system(size: 16))
                            .foregroundStyle(palette.onSurfaceVariant)
                            .frame(width: 40, height: 56)
                    }
                    .buttonStyle(.plain)
                }
            }
        case .forget:
            SettingsDialogCard(
                icon: "iphone",
                title: "Forget this device?",
                palette: palette,
                confirmLabel: "Forget",
                destructive: true,
                onConfirm: {
                    self.dialog = nil
                    Task {
                        _ = await model.forgetDevice()
                        await model.connect()
                    }
                },
                onDismiss: { self.dialog = nil }
            ) {
                Text("A new anonymous session starts on this phone. Existing links stay online.")
                    .font(JuiceFont.body(14))
                    .foregroundStyle(palette.onSurfaceVariant)
            }
        case .clear:
            SettingsDialogCard(
                icon: "trash",
                title: "Clear local library?",
                palette: palette,
                confirmLabel: "Clear",
                destructive: true,
                onConfirm: {
                    self.dialog = nil
                    Task { _ = await model.clearLibrary() }
                },
                onDismiss: { self.dialog = nil }
            ) {
                Text(
                    "Every saved file and link token leaves this phone. Files already " +
                    "uploaded stay online until they expire."
                )
                .font(JuiceFont.body(14))
                .foregroundStyle(palette.onSurfaceVariant)
            }
        }
    }

    private func groupLabel(_ text: String, _ palette: JuicePalette) -> some View {
        Text(text)
            .font(JuiceFont.body(14, weight: .medium))
            .foregroundStyle(palette.onSurfaceVariant)
            .padding(.leading, 6)
    }
}

/// A JSON library export, handed to the system document picker.
struct LibraryDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }

    var text: String

    init(text: String) {
        self.text = text
    }

    init(configuration: ReadConfiguration) throws {
        guard let data = configuration.file.regularFileContents,
              let string = String(data: data, encoding: .utf8) else {
            throw CocoaError(.fileReadCorruptFile)
        }
        text = string
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: Data(text.utf8))
    }
}
