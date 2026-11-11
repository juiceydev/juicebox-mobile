import Foundation
import XCTest

/// Drives the parts of the app that only appear outside it: the Live Activity
/// on the Dynamic Island, its expanded state, and Notification Centre.
///
/// The app starts a demo upload when launched with JUICEBOX_DEMO_UPLOAD=1;
/// this test never touches the network.
final class DynamicIslandUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testLiveActivityDynamicIsland() throws {
        let app = XCUIApplication()
        app.launchEnvironment["JUICEBOX_DEMO_UPLOAD"] = "1"
        app.launchEnvironment["JUICEBOX_DEMO_LIBRARY"] = "1"
        app.launch()

        // Let the Live Activity register and take its first progress update.
        sleep(4)
        snap("dynamic-island-in-app")

        XCUIDevice.shared.press(.home)
        sleep(3)
        snap("dynamic-island-compact")

        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let island = springboard.coordinate(
            withNormalizedOffset: CGVector(dx: 0.5, dy: 0.041)
        )
        island.press(forDuration: 1.2)
        sleep(3)
        snap("dynamic-island-expanded")

        // Collapse the island before opening Notification Centre.
        island.press(forDuration: 0.1)
        sleep(1)

        let top = springboard.coordinate(
            withNormalizedOffset: CGVector(dx: 0.12, dy: 0.004)
        )
        let bottom = springboard.coordinate(
            withNormalizedOffset: CGVector(dx: 0.12, dy: 0.72)
        )
        top.press(forDuration: 0.05, thenDragTo: bottom)
        sleep(3)
        snap("dynamic-island-notification-centre")
    }

    private func snap(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
