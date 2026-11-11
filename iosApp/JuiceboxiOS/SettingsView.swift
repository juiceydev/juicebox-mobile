import JuiceboxCore
import SwiftUI
import UniformTypeIdentifiers

struct SettingsView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.colorScheme) private var scheme
    @Environment(\.openURL) private var openURL

    @State private var path: [SettingsPage]
    @State private var dialog: SettingsDialog?
    @State private var launchDialog: SettingsDialog?

    @State private var serverInput = ""
    @State private var serverBusy = false
    @State private var customHost = ""
    @State private var customHostBusy = false
    @State private var customHostNotice: String?
    @State private var customHostOk = false

    @State private var passwordDraft = ""

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

        var icon: String {
            switch self {
            case .server: return "globe"
            case .storage: return "cloud"
            case .appearance: return "moon"
            case .uploads: return "arrow.up.circle"
            case .links: return "link"
            case .library: return "folder"
            case .about: return "info.circle"
            case .session: return "iphone"
            }
        }
    }

    enum SettingsDialog: Hashable {
        case share, password, forget, clear
    }

    /// Screenshot runs can launch straight onto a subpage with
    /// SIMCTL_CHILD_JUICEBOX_START_PAGE, and open a dialog with
    /// SIMCTL_CHILD_JUICEBOX_START_DIALOG.
    init() {
        let environment = ProcessInfo.processInfo.environment
        let start = environment["JUICEBOX_START_PAGE"]
            .flatMap { SettingsPage(rawValue: $0) }
        _path = State(initialValue: start.map { [$0] } ?? [])
        _launchDialog = State(initialValue: Self.dialog(named: environment["JUICEBOX_START_DIALOG"]))
    }

    private static func dialog(named raw: String?) -> SettingsDialog? {
        switch raw {
        case "share": return .share
        case "password": return .password
        case "forget": return .forget
        case "clear": return .clear
        default: return nil
        }
    }

    var body: some View {
        let palette = model.palette(scheme)
        NavigationStack(path: $path) {
            root(palette)
                .navigationTitle("Settings")
                .navigationDestination(for: SettingsPage.self) { page in
                    pageBody(page, palette)
                        .navigationTitle(page.title)
                        .navigationBarTitleDisplayMode(.inline)
                }
        }
        .tint(palette.primary)
        .alert(
            "Forget this device?",
            isPresented: presented(.forget)
        ) {
            Button("Cancel", role: .cancel) {}
            Button("Forget", role: .destructive) { forgetDevice() }
        } message: {
            Text("A new anonymous session starts on this phone. Existing links stay online.")
        }
        .task {
            guard let pending = launchDialog else { return }
            launchDialog = nil
            try? await Task.sleep(for: .milliseconds(450))
            dialog = pending
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
        settingsList(palette) {
            Section {
                TitleWordmark(size: 16, color: palette.primary)
                    .listRowBackground(Color.clear)
                    .listRowInsets(
                        EdgeInsets(top: 0, leading: 0, bottom: 4, trailing: 0)
                    )
                    .listRowSeparator(.hidden)
            }

            Section {
                ForEach(
                    [SettingsPage.server, .storage, .appearance, .uploads, .links, .library, .about],
                    id: \.self
                ) { page in
                    NavigationLink(value: page) {
                        Label(page.title, systemImage: page.icon)
                            .foregroundStyle(palette.onSurface)
                    }
                }
            }
            .listRowBackground(ListRowSurface(palette: palette))
        }
        .listSectionSpacing(16)
    }

    @ViewBuilder
    private func pageBody(_ page: SettingsPage, _ palette: JuicePalette) -> some View {
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

    /// A native grouped list wearing the brand palette. Every page is a List,
    /// so it gets system row insets, selection, swipe-back and separators.
    private func settingsList<Content: View>(
        _ palette: JuicePalette,
        @ViewBuilder content: () -> Content
    ) -> some View {
        List {
            content()
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .background(palette.background.ignoresSafeArea())
        .tint(palette.primary)
        .font(JuiceFont.body(16))
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
        settingsList(palette) {
            Section {
                TextField("Server URL", text: $serverInput)
                    .keyboardType(.URL)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                LabeledContent("Status", value: connectionLabel)
            }
            .listRowBackground(ListRowSurface(palette: palette))

            Section {
                Button {
                    saveServer()
                } label: {
                    HStack(spacing: 8) {
                        if serverBusy {
                            ProgressView()
                        }
                        Text("Save")
                    }
                    .frame(maxWidth: .infinity)
                }
                .disabled(
                    serverBusy ||
                    serverInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                )
            }
            .listRowBackground(ListRowSurface(palette: palette))

            if let config = model.config {
                Section("Limits") {
                    LabeledContent(
                        "Largest file",
                        value: JuiceFormat.bytes(config.maxFileSizeBytes)
                    )
                    LabeledContent(
                        "Retention",
                        value: "up to \(TtlFormat.label(minutes: Int32((config.maxTtlHours * 60).rounded())))"
                    )
                }
                .listRowBackground(ListRowSurface(palette: palette))
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
        let known = Set((model.storageHosts + model.communityHosts).map(\.url))
        return model.settings.customHosts.filter { !known.contains($0) }
    }

    private var storageSelection: Binding<String?> {
        Binding(
            get: { model.settings.storageHost },
            set: { model.setStorageHost($0) }
        )
    }

    private func hostName(_ url: String) -> String {
        url
            .replacingOccurrences(of: "https://", with: "")
            .replacingOccurrences(of: "http://", with: "")
            .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
    }

    private func storagePage(_ palette: JuicePalette) -> some View {
        settingsList(palette) {
            Section {
                Picker("Storage", selection: storageSelection) {
                    Text("Auto").tag(Optional<String>.none)
                    ForEach(model.storageHosts, id: \.url) { host in
                        Text(host.name).tag(Optional(host.url))
                    }
                }
                .pickerStyle(.inline)
                .labelsHidden()
            }
            .listRowBackground(ListRowSurface(palette: palette))

            Section("Community") {
                if model.communityHosts.isEmpty {
                    Text(
                        model.communityHostsLoaded
                            ? "No community nodes listed yet."
                            : "Checking for community nodes..."
                    )
                    .foregroundStyle(palette.onSurfaceVariant)
                } else {
                    Picker("Community", selection: storageSelection) {
                        ForEach(model.communityHosts, id: \.url) { host in
                            Text(host.name).tag(Optional(host.url))
                        }
                    }
                    .pickerStyle(.inline)
                    .labelsHidden()
                }
            }
            .listRowBackground(ListRowSurface(palette: palette))

            if !savedHosts.isEmpty {
                Section("Saved nodes") {
                    Picker("Saved nodes", selection: storageSelection) {
                        ForEach(savedHosts, id: \.self) { url in
                            Text(hostName(url)).tag(Optional(url))
                        }
                    }
                    .pickerStyle(.inline)
                    .labelsHidden()
                }
                .listRowBackground(ListRowSurface(palette: palette))
            }

            Section {
                TextField("Node URL", text: $customHost)
                    .keyboardType(.URL)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                Button {
                    checkHost()
                } label: {
                    HStack(spacing: 8) {
                        if customHostBusy {
                            ProgressView()
                        }
                        Text("Check")
                    }
                }
                .disabled(customHostBusy)
            } header: {
                Text("Custom node")
            } footer: {
                if let customHostNotice {
                    Text(customHostNotice)
                        .foregroundStyle(customHostOk ? palette.primary : palette.error)
                }
            }
            .listRowBackground(ListRowSurface(palette: palette))
        }
        .onAppear { model.loadCommunityHosts() }
    }

    private func checkHost() {
        var value = customHost.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else {
            model.setStorageHost(nil)
            return
        }
        if !value.contains("://") { value = "https://" + value }
        while value.hasSuffix("/") { value.removeLast() }
        customHostBusy = true
        customHostNotice = nil
        Task {
            let result = await model.validateHost(value)
            customHostBusy = false
            if let result, result.accepted {
                model.saveCustomHost(value)
                customHost = value
                customHostNotice = "Online"
                customHostOk = true
            } else {
                customHostNotice = "Not reachable"
                customHostOk = false
            }
        }
    }

    // MARK: - Appearance

    private func appearancePage(_ palette: JuicePalette) -> some View {
        let current = JuiceTheme.normalized(model.settings.themeMode)
        return settingsList(palette) {
            Section {
                ForEach(JuiceTheme.modes) { theme in
                    Button {
                        model.setTheme(theme.id)
                    } label: {
                        HStack(spacing: 10) {
                            Text(theme.label)
                                .foregroundStyle(palette.onSurface)
                            Spacer(minLength: 8)
                            HStack(spacing: 4) {
                                ForEach(Array(swatches(theme.id).enumerated()), id: \.offset) { _, color in
                                    Circle()
                                        .fill(color)
                                        .frame(width: 14, height: 14)
                                }
                            }
                            if current == theme.id {
                                Image(systemName: "checkmark")
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundStyle(palette.primary)
                            }
                        }
                    }
                }
            } header: {
                Text("Theme")
            }
            .listRowBackground(ListRowSurface(palette: palette))

            if #available(iOS 26.0, *) {
                Section {
                    Toggle(
                        "Liquid Glass",
                        isOn: Binding(
                            get: { model.settings.liquidGlass },
                            set: { model.setLiquidGlass($0) }
                        )
                    )
                }
                .listRowBackground(ListRowSurface(palette: palette))
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
        settingsList(palette) {
            Section {
                Toggle(
                    "Quick link",
                    isOn: Binding(
                        get: { model.settings.quickLink },
                        set: { model.setQuickLink($0) }
                    )
                )
                Toggle(
                    "Copy link automatically",
                    isOn: Binding(
                        get: { model.settings.autoCopy },
                        set: { model.setAutoCopy($0) }
                    )
                )
                Toggle(
                    "Haptics",
                    isOn: Binding(
                        get: { model.settings.haptics },
                        set: { model.setHaptics($0) }
                    )
                )
                Button {
                    dialog = .share
                } label: {
                    HStack {
                        Text("Shared links")
                            .foregroundStyle(palette.onSurface)
                        Spacer(minLength: 8)
                        Text(shareModeLabel)
                            .foregroundStyle(palette.onSurfaceVariant)
                        Image(systemName: "chevron.right")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(palette.outline)
                    }
                }
                .alert("Shared links", isPresented: presented(.share)) {
                    Button("Fetch video") {
                        model.setShareLinkMode("fetch")
                        dialog = nil
                    }
                    Button("Upload as text") {
                        model.setShareLinkMode("text")
                        dialog = nil
                    }
                    Button("Cancel", role: .cancel) { dialog = nil }
                } message: {
                    Text("What a link shared into Juicebox becomes on this phone.")
                }
            }
            .listRowBackground(ListRowSurface(palette: palette))

            Section {
                Toggle(
                    "Lock uploads",
                    isOn: Binding(
                        get: { model.settings.lockUploads },
                        set: { checked in
                            if checked {
                                passwordDraft = model.settings.uploadPassword
                                dialog = .password
                            } else {
                                model.setLockUploads(false)
                            }
                        }
                    )
                )
                if model.settings.lockUploads && !model.settings.uploadPassword.isEmpty {
                    Button {
                        passwordDraft = model.settings.uploadPassword
                        dialog = .password
                    } label: {
                        Label("Change password", systemImage: "key")
                            .foregroundStyle(palette.onSurface)
                    }
                }
            }
            .listRowBackground(ListRowSurface(palette: palette))
        }
        .alert("Upload password", isPresented: presented(.password)) {
            SecureField("Password", text: $passwordDraft)
            Button("Cancel", role: .cancel) { dialog = nil }
            Button("Save") { savePassword() }
                .disabled(
                    passwordDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                )
        } message: {
            Text("Uploads from this phone will ask for this password.")
        }
    }

    private func savePassword() {
        let value = passwordDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { return }
        model.setUploadPassword(value)
        model.setLockUploads(true)
        dialog = nil
    }

    // MARK: - Default link

    private func linksPage(_ palette: JuicePalette) -> some View {
        settingsList(palette) {
            Section {
                Picker(
                    "Default link",
                    selection: Binding(
                        get: { model.settings.linkVariant },
                        set: { model.setLinkVariant($0) }
                    )
                ) {
                    Text("Preview").tag("preview")
                    Text("Raw").tag("raw")
                    Text("Download").tag("download")
                }
                .pickerStyle(.inline)
                .labelsHidden()
            } footer: {
                Text("Which link Juicebox copies after an upload.")
            }
            .listRowBackground(ListRowSurface(palette: palette))
        }
    }

    // MARK: - Library

    private func libraryPage(_ palette: JuicePalette) -> some View {
        settingsList(palette) {
            Section {
                LabeledContent("Files", value: "\(model.libraryCount)")
                Toggle("Include identity", isOn: $includeSession)
                HStack(spacing: 10) {
                    Button {
                        runExport()
                    } label: {
                        Label("Export", systemImage: "arrow.down.circle")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    Button {
                        showImporter = true
                    } label: {
                        Label("Import", systemImage: "arrow.up.circle")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                }
                .controlSize(.large)
            } header: {
                Text("On this device")
            }
            .listRowBackground(ListRowSurface(palette: palette))

            Section {
                Button(role: .destructive) {
                    dialog = .clear
                } label: {
                    Label("Clear local library", systemImage: "trash")
                }
            }
            .listRowBackground(ListRowSurface(palette: palette))
        }
        .alert("Clear local library?", isPresented: presented(.clear)) {
            Button("Cancel", role: .cancel) { dialog = nil }
            Button("Clear", role: .destructive) {
                dialog = nil
                Task { _ = await model.clearLibrary() }
            }
        } message: {
            Text(
                "Every saved file and link token leaves this phone. Files already " +
                "uploaded stay online until they expire."
            )
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
        return settingsList(palette) {
            Section {
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
            }
            .listRowBackground(ListRowSurface(palette: palette))

            Section {
                NavigationLink(value: SettingsPage.session) {
                    Label("This session", systemImage: "iphone")
                        .foregroundStyle(palette.onSurface)
                    Spacer()
                    Text(active ? "Active" : "None")
                        .foregroundStyle(palette.onSurfaceVariant)
                }
                Button {
                    openSite()
                } label: {
                    Label("Open site", systemImage: "globe")
                        .foregroundStyle(palette.onSurface)
                }
                Button(role: .destructive) {
                    dialog = .forget
                } label: {
                    Label("Forget device", systemImage: "iphone")
                }
            }
            .listRowBackground(ListRowSurface(palette: palette))
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
        return settingsList(palette) {
            Section {
                HStack(spacing: 14) {
                    Image(systemName: "iphone")
                        .font(.system(size: 24, weight: .regular))
                        .foregroundStyle(palette.onPrimaryContainer)
                        .frame(width: 48, height: 48)
                        .background(palette.primaryContainer, in: Circle())
                    VStack(alignment: .leading, spacing: 2) {
                        Text(active ? "Active session" : "No session yet")
                            .font(JuiceFont.body(16, weight: .medium))
                            .foregroundStyle(palette.onSurface)
                        Text(
                            active
                                ? "Anonymous identity saved on this phone"
                                : "Connect to start one"
                        )
                        .font(JuiceFont.body(12))
                        .foregroundStyle(palette.onSurfaceVariant)
                    }
                    Spacer(minLength: 0)
                }
                LabeledContent("Server", value: host)
                LabeledContent("Library", value: "\(model.libraryCount) files")
                LabeledContent("App", value: "v\(model.version)")
            }
            .listRowBackground(ListRowSurface(palette: palette))

            Section {
                Button(role: .destructive) {
                    dialog = .forget
                } label: {
                    Label("Forget device", systemImage: "iphone")
                }
            }
            .listRowBackground(ListRowSurface(palette: palette))
        }
    }

    // MARK: - Dialogs

    private func presented(_ target: SettingsDialog) -> Binding<Bool> {
        Binding(
            get: { dialog == target },
            set: { shown in
                if !shown && dialog == target { dialog = nil }
            }
        )
    }

    private func forgetDevice() {
        dialog = nil
        Task {
            _ = await model.forgetDevice()
            await model.connect()
        }
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
