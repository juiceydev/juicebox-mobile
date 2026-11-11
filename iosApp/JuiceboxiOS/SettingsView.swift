import JuiceboxCore
import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.colorScheme) private var scheme

    @State private var showServerEditor = false
    @State private var confirmForget = false
    @State private var password = ""

    var body: some View {
        let palette = model.palette(scheme)
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                header(palette)
                serverCard(palette)
                appearanceCard(palette)
                uploadsCard(palette)
                aboutCard(palette)
            }
            .padding(.bottom, 30)
        }
        .background(palette.background.ignoresSafeArea())
        .onAppear {
            password = model.settings.uploadPassword
        }
        .onChange(of: model.settings.uploadPassword) { _, value in
            if value != password { password = value }
        }
        .sheet(isPresented: $showServerEditor) {
            ServerEditorView(current: model.settings.serverUrl)
                .environmentObject(model)
        }
        .confirmationDialog(
            "Forget this device?",
            isPresented: $confirmForget,
            titleVisibility: .visible
        ) {
            Button("Forget device", role: .destructive) {
                Task {
                    if await model.forgetDevice() {
                        model.flash("Session forgotten")
                    }
                    await model.connect()
                }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("The server will no longer recognise this session. Your local library stays.")
        }
    }

    // MARK: - Header

    private func header(_ palette: JuicePalette) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Settings")
                .font(.system(size: 38, weight: .bold, design: .rounded))
                .foregroundStyle(palette.onSurface)
            Text("v\(model.version) for iOS")
                .font(.system(size: 12, design: .monospaced))
                .foregroundStyle(palette.onSurfaceVariant)
        }
        .padding(.horizontal, 20)
        .padding(.top, 18)
    }

    // MARK: - Server

    private func serverCard(_ palette: JuicePalette) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionLabel(text: "Server", palette: palette)
            SectionCard(palette: palette) {
                HStack(spacing: 12) {
                    Image(systemName: "globe")
                        .font(.system(size: 17, weight: .medium))
                        .foregroundStyle(palette.primary)
                        .frame(width: 30)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(model.serverHost)
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(palette.onSurface)
                            .lineLimit(1)
                        Text(model.statusText)
                            .font(.system(size: 12, design: .monospaced))
                            .foregroundStyle(model.connection.connected ? palette.success : palette.onSurfaceVariant)
                    }
                    Spacer()
                    Button("Change") {
                        showServerEditor = true
                    }
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(palette.primary)
                    .buttonStyle(.plain)
                }
                .padding(16)
            }
        }
    }

    // MARK: - Appearance

    private func appearanceCard(_ palette: JuicePalette) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionLabel(text: "Appearance", palette: palette)
            SectionCard(palette: palette) {
                LazyVGrid(
                    columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)],
                    spacing: 10
                ) {
                    ForEach(JuiceTheme.modes) { theme in
                        themeButton(theme, palette)
                    }
                }
                .padding(14)
                Text("Material You is Android-only; iOS keeps the five static palettes.")
                    .font(.system(size: 11))
                    .foregroundStyle(palette.onSurfaceVariant)
                    .padding(.horizontal, 18)
                    .padding(.bottom, 14)
            }
        }
    }

    private func themeButton(_ theme: JuiceTheme.Mode, _ palette: JuicePalette) -> some View {
        let preview = JuiceTheme.palette(mode: theme.id, dark: scheme == .dark)
        let selected = JuiceTheme.normalized(model.settings.themeMode) == theme.id
        return Button {
            model.setTheme(theme.id)
        } label: {
            HStack(spacing: 10) {
                Circle()
                    .fill(preview.primary)
                    .frame(width: 20, height: 20)
                Text(theme.label)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(palette.onSurface)
                Spacer()
                if selected {
                    Image(systemName: "checkmark")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(palette.primary)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 12)
            .background(
                selected ? palette.primary.opacity(0.1) : palette.surfaceContainerHigh.opacity(0.6),
                in: RoundedRectangle(cornerRadius: 14, style: .continuous)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .strokeBorder(selected ? palette.primary.opacity(0.6) : Color.clear, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }

    // MARK: - Uploads

    private func uploadsCard(_ palette: JuicePalette) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionLabel(text: "Uploads", palette: palette)
            SectionCard(palette: palette) {
                toggleRow(
                    "Quick link",
                    detail: "Show the link before the upload finishes.",
                    icon: "bolt",
                    isOn: Binding(
                        get: { model.settings.quickLink },
                        set: { model.setQuickLink($0) }
                    ),
                    palette: palette
                )
                divider(palette)
                toggleRow(
                    "Copy after upload",
                    detail: "Put the link on the clipboard automatically.",
                    icon: "doc.on.clipboard",
                    isOn: Binding(
                        get: { model.settings.autoCopy },
                        set: { model.setAutoCopy($0) }
                    ),
                    palette: palette
                )
                divider(palette)
                toggleRow(
                    "Haptics",
                    detail: "Tap feedback for rows and controls.",
                    icon: "iphone.radiowaves.left.and.right",
                    isOn: Binding(
                        get: { model.settings.haptics },
                        set: { model.setHaptics($0) }
                    ),
                    palette: palette
                )
                divider(palette)
                toggleRow(
                    "Lock uploads",
                    detail: "Protect every upload with one password.",
                    icon: "lock",
                    isOn: Binding(
                        get: { model.settings.lockUploads },
                        set: { model.setLockUploads($0) }
                    ),
                    palette: palette
                )
                if model.settings.lockUploads {
                    divider(palette)
                    HStack(spacing: 10) {
                        Image(systemName: "key")
                            .font(.system(size: 15))
                            .foregroundStyle(palette.primary)
                            .frame(width: 30)
                        SecureField("Upload password", text: $password)
                            .font(.system(size: 15))
                            .foregroundStyle(palette.onSurface)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .onSubmit {
                                model.setUploadPassword(password)
                                model.flash("Password saved")
                            }
                        Button("Save") {
                            model.setUploadPassword(password)
                            model.flash("Password saved")
                        }
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(palette.primary)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    if password.isEmpty {
                        Text("Set a password before uploading, or the lock has no effect.")
                            .font(.system(size: 11))
                            .foregroundStyle(palette.onSurfaceVariant)
                            .padding(.horizontal, 18)
                            .padding(.bottom, 12)
                    }
                }
                divider(palette)
                HStack(spacing: 10) {
                    Image(systemName: "link")
                        .font(.system(size: 15))
                        .foregroundStyle(palette.primary)
                        .frame(width: 30)
                    Picker(
                        "Link",
                        selection: Binding(
                            get: { model.settings.linkVariant },
                            set: { model.setLinkVariant($0) }
                        )
                    ) {
                        Text("Preview").tag("preview")
                        Text("Raw").tag("raw")
                        Text("Download").tag("download")
                    }
                    .pickerStyle(.segmented)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 14)
            }
        }
    }

    private func toggleRow(
        _ title: String,
        detail: String,
        icon: String,
        isOn: Binding<Bool>,
        palette: JuicePalette
    ) -> some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .font(.system(size: 15))
                .foregroundStyle(palette.primary)
                .frame(width: 30)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(palette.onSurface)
                Text(detail)
                    .font(.system(size: 11))
                    .foregroundStyle(palette.onSurfaceVariant)
            }
            Spacer(minLength: 6)
            Toggle("", isOn: isOn)
                .labelsHidden()
                .tint(palette.primary)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }

    private func divider(_ palette: JuicePalette) -> some View {
        Divider()
            .overlay(palette.outline.opacity(0.25))
            .padding(.leading, 56)
    }

    // MARK: - About

    private func aboutCard(_ palette: JuicePalette) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionLabel(text: "About", palette: palette)
            SectionCard(palette: palette) {
                infoRow("App", value: "v\(model.version)", palette: palette)
                divider(palette)
                infoRow("Server", value: model.serverHost, palette: palette)
                divider(palette)
                infoRow(
                    "Session",
                    value: model.connection.connected ? "Active" : "Inactive",
                    palette: palette
                )
                divider(palette)
                Button {
                    confirmForget = true
                } label: {
                    HStack(spacing: 10) {
                        Image(systemName: "iphone.slash")
                            .font(.system(size: 15))
                            .frame(width: 30)
                        Text("Forget device")
                            .font(.system(size: 15, weight: .semibold))
                        Spacer()
                    }
                    .foregroundStyle(palette.error)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 14)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func infoRow(_ title: String, value: String, palette: JuicePalette) -> some View {
        HStack {
            Text(title)
                .font(.system(size: 15))
                .foregroundStyle(palette.onSurface)
            Spacer()
            Text(value)
                .font(.system(size: 13, design: .monospaced))
                .foregroundStyle(palette.onSurfaceVariant)
                .lineLimit(1)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
    }
}

struct ServerEditorView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dismiss) private var dismiss

    @State private var input: String
    @State private var busy = false

    init(current: String) {
        _input = State(initialValue: current)
    }

    var body: some View {
        let palette = model.palette(scheme)
        VStack(alignment: .leading, spacing: 18) {
            HStack {
                Text("Server")
                    .font(.system(size: 24, weight: .bold, design: .rounded))
                    .foregroundStyle(palette.onSurface)
                Spacer()
                Button {
                    dismiss()
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(palette.onSurfaceVariant)
                        .frame(width: 30, height: 30)
                        .background(palette.surfaceContainerHigh, in: Circle())
                }
                .buttonStyle(.plain)
            }

            Text("Leave off the scheme if you like; https is assumed.")
                .font(.system(size: 12))
                .foregroundStyle(palette.onSurfaceVariant)

            TextField("box.juicey.dev", text: $input)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .keyboardType(.URL)
                .font(.system(size: 16))
                .foregroundStyle(palette.onSurface)
                .padding(.horizontal, 16)
                .padding(.vertical, 14)
                .background(palette.surfaceContainer, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .strokeBorder(palette.outline.opacity(0.5), lineWidth: 1)
                )

            Button {
                Task {
                    busy = true
                    let ok = await model.applyServer(input)
                    busy = false
                    if ok { dismiss() }
                }
            } label: {
                HStack(spacing: 8) {
                    if busy { ProgressView().tint(palette.onPrimary) }
                    Text(busy ? "Connecting" : "Connect")
                        .font(.system(size: 15, weight: .semibold))
                }
                .foregroundStyle(palette.onPrimary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(palette.primary, in: Capsule())
            }
            .buttonStyle(.plain)
            .disabled(busy || input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            .opacity(busy || input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? 0.6 : 1)

            Spacer()
        }
        .padding(22)
        .background(palette.background.ignoresSafeArea())
        .presentationDetents([.height(330)])
        .presentationDragIndicator(.visible)
    }
}
