import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        ComposeHostController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

private final class ComposeHostController: UIViewController {
    private let geometry = PlayerWindowGeometry()
    private lazy var compose = MainViewControllerKt.MainViewController(geometry: geometry)

    override func loadView() {
        let host = ReservedRegionView()
        host.onLayout = { [weak self] in self?.updateRegions() }
        view = host
        addChild(compose)
        host.addSubview(compose.view)
        compose.view.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            compose.view.leadingAnchor.constraint(equalTo: host.leadingAnchor),
            compose.view.trailingAnchor.constraint(equalTo: host.trailingAnchor),
            compose.view.topAnchor.constraint(equalTo: host.topAnchor),
            compose.view.bottomAnchor.constraint(equalTo: host.bottomAnchor),
        ])
        compose.didMove(toParent: self)
    }

    private func updateRegions() {
        guard #available(iOS 27.1, *) else { return }
        // Query in updateConstraints for UIKit's automatic invalidation on pose changes.
        // Frames already include the system's interactive-content margins.
        let regions = [UIView.ReservedRegion.Kind.division, .occlusion].flatMap { kind in
            view.reservedRegions(kind: kind).filter(\.isActive).map { region in
                let frame = compose.view.convert(region.frame, from: view)
                return PlayerReservedRegion(
                    left: frame.minX, top: frame.minY,
                    right: frame.maxX, bottom: frame.maxY,
                    division: kind == .division
                )
            }
        }
        geometry.update(regions: regions)
    }
}

private final class ReservedRegionView: UIView {
    var onLayout: (() -> Void)?

    override func updateConstraints() {
        onLayout?()
        super.updateConstraints()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        onLayout?()
    }
}

struct ContentView: View {
    var body: some View {
        ComposeView()
                .ignoresSafeArea(.keyboard) // Compose has own keyboard handler
                .ignoresSafeArea(.container) // Extend to screen edges (removes white areas)
    }
}
