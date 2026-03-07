import SwiftUI

@main
struct NearbyGlassesApp: App {
    init() {
        PreferencesManager.shared.load()
    }

    var body: some Scene {
        WindowGroup {
            MainView()
        }
    }
}
