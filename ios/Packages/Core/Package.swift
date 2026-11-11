// swift-tools-version: 6.0
import PackageDescription

// Pure Swift: models, data contract, demo world and derived logic.
// No Firebase dependency, so it builds and tests fast.
let package = Package(
    name: "Core",
    platforms: [.iOS(.v18), .macOS(.v15)],
    products: [
        .library(name: "Core", targets: ["Core"]),
    ],
    targets: [
        .target(name: "Core"),
        .testTarget(name: "CoreTests", dependencies: ["Core"]),
    ]
)
