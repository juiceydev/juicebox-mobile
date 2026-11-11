import Foundation
import XCTest

/// Drives the parts of the app that only appear outside it: the Live Activity
/// on the Dynamic Island, its expanded state, and Notification Centre.
///
/// The app starts a demo upload when launched with JUICEBOX_DEMO_UPLOAD=1;
/// this test never touches the network.
final class DynamicIslandUITests: XCTestCase {
    private let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")

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
        allowLiveActivities()
        // The request may have been waiting on the permission prompt, so give
        // the compact island a moment to pick it up before the in-app capture.
        sleep(2)
        snap("dynamic-island-in-app")

        XCUIDevice.shared.press(.home)
        sleep(2)
        allowLiveActivities()
        sleep(1)
        snap("dynamic-island-compact")

        let island = springboard.coordinate(
            withNormalizedOffset: CGVector(dx: 0.5, dy: 0.041)
        )
        island.press(forDuration: 1.2)
        sleep(3)
        allowLiveActivities()
        snap("dynamic-island-expanded")

        // Collapse the island before opening Notification Centre.
        island.press(forDuration: 0.1)
        sleep(1)
        allowLiveActivities()

        let top = springboard.coordinate(
            withNormalizedOffset: CGVector(dx: 0.12, dy: 0.004)
        )
        let bottom = springboard.coordinate(
            withNormalizedOffset: CGVector(dx: 0.12, dy: 0.72)
        )
        top.press(forDuration: 0.05, thenDragTo: bottom)
        sleep(2)
        // The first activity on a fresh simulator asks for permission; answer
        // it so the capture shows the activity, not the alert.
        allowLiveActivities()
        sleep(1)
        snap("dynamic-island-notification-centre")
    }

    /// iOS asks the user to allow Live Activities the first time an app starts
    /// one. The prompt belongs to SpringBoard, not the app process.
    @MainActor
    private func allowLiveActivities() {
        for label in ["Allow", "Allow Once", "Allow While Using App"] {
            let button = springboard.buttons[label].firstMatch
            if button.waitForExistence(timeout: 1), button.isHittable {
                button.tap()
                sleep(1)
                return
            }
        }
    }

    private func snap(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
