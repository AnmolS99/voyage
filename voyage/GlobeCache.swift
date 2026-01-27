import SceneKit
import Foundation

class GlobeCache {
    static let shared = GlobeCache()

    private var bundledGlobeURL: URL? {
        Bundle.main.url(forResource: "globe", withExtension: "scn")
    }

    func loadBundledGlobe() -> SCNNode? {
        guard let url = bundledGlobeURL else {
            print("Bundled globe.scn not found")
            return nil
        }

        do {
            let scene = try SCNScene(url: url, options: nil)
            return scene.rootNode.childNode(withName: "globe", recursively: true)?.clone()
        } catch {
            print("Failed to load bundled globe: \(error)")
            return nil
        }
    }
}
