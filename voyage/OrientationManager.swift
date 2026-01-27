import SwiftUI
import UIKit

class OrientationManager: ObservableObject {
    static let shared = OrientationManager()

    @Published var isLandscapeLocked: Bool = false

    var supportedOrientations: UIInterfaceOrientationMask {
        if isLandscapeLocked {
            return .landscape
        }
        return .all
    }

    func lockToLandscape() {
        isLandscapeLocked = true
        rotateToLandscape()
    }

    func unlock() {
        isLandscapeLocked = false
    }

    private func rotateToLandscape() {
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene else { return }

        let currentOrientation = windowScene.interfaceOrientation
        if !currentOrientation.isLandscape {
            windowScene.requestGeometryUpdate(.iOS(interfaceOrientations: .landscape)) { error in
                // Orientation change requested
            }
        }

        setNeedsOrientationUpdate()
    }

    func setNeedsOrientationUpdate() {
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene else { return }
        for window in windowScene.windows {
            window.rootViewController?.setNeedsUpdateOfSupportedInterfaceOrientations()
        }
    }
}
