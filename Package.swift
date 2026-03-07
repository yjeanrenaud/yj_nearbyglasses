// swift-tools-version: 5.9
// This file is provided for reference. Build using the Xcode project.
import PackageDescription

let package = Package(
    name: "NearbyGlasses",
    platforms: [.iOS(.v16)],
    targets: [
        .executableTarget(
            name: "NearbyGlasses",
            path: "NearbyGlasses/Sources",
            resources: [
                .process("../Resources")
            ]
        )
    ]
)
