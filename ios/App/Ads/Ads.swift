import AppTrackingTransparency
import DesignSystem
import GoogleMobileAds
import Observation
import SwiftUI
import UserMessagingPlatform

/// Gathers consent (Google UMP for GDPR / US states, then Apple's tracking
/// prompt) and only then starts the Mobile Ads SDK. Ads are small and
/// in-flow: native cards styled like our content, and inline banners at the
/// end of long lists. Never full screen.
@MainActor
@Observable
final class AdsController {
    private(set) var canShowAds = false
    private(set) var privacyOptionsRequired = false
    private var started = false

    /// Google's public test units in debug builds; real units come from Info.plist in release.
    var nativeUnitID: String {
        #if DEBUG
        "ca-app-pub-3940256099942544/3986624511"
        #else
        Bundle.main.object(forInfoDictionaryKey: "TOMNativeAdUnitID") as? String ?? ""
        #endif
    }

    var bannerUnitID: String {
        #if DEBUG
        "ca-app-pub-3940256099942544/2435281174"
        #else
        Bundle.main.object(forInfoDictionaryKey: "TOMBannerAdUnitID") as? String ?? ""
        #endif
    }

    func gatherConsent() {
        Task {
            do {
                try await ConsentInformation.shared.requestConsentInfoUpdate(with: RequestParameters())
                if let root = Self.rootViewController {
                    try await ConsentForm.loadAndPresentIfRequired(from: root)
                }
            } catch {
                // Consent errors must not block the app; ads simply stay off.
            }
            privacyOptionsRequired = ConsentInformation.shared.privacyOptionsRequirementStatus == .required
            if ATTrackingManager.trackingAuthorizationStatus == .notDetermined {
                _ = await ATTrackingManager.requestTrackingAuthorization()
            }
            startIfAllowed()
        }
    }

    func showPrivacyOptions() {
        guard let root = Self.rootViewController else { return }
        Task {
            try? await ConsentForm.presentPrivacyOptionsForm(from: root)
            startIfAllowed()
        }
    }

    private func startIfAllowed() {
        guard ConsentInformation.shared.canRequestAds, !started else { return }
        started = true
        MobileAds.shared.start { [weak self] _ in
            Task { @MainActor in self?.canShowAds = true }
        }
    }

    static var rootViewController: UIViewController? {
        UIApplication.shared.connectedScenes.compactMap { ($0 as? UIWindowScene)?.keyWindow?.rootViewController }.first
    }
}

// MARK: - Native card

/// A native ad drawn like our own cards, with a clear "Ad" label.
/// Collapses to nothing if no ad loads.
struct NativeAdCard: View {
    @Environment(AdsController.self) private var ads
    @State private var loader = NativeAdLoader()

    var body: some View {
        Group {
            if ads.canShowAds, let ad = loader.ad {
                NativeAdRepresentable(ad: ad)
                    .frame(height: 84)
                    .transition(.opacity)
            }
        }
        .task(id: ads.canShowAds) {
            if ads.canShowAds { loader.load(unitID: ads.nativeUnitID) }
        }
    }
}

@MainActor
@Observable
final class NativeAdLoader: NSObject, NativeAdLoaderDelegate {
    private(set) var ad: NativeAd?
    private var adLoader: AdLoader?

    func load(unitID: String) {
        guard adLoader == nil, !unitID.isEmpty else { return }
        let loader = AdLoader(adUnitID: unitID, rootViewController: AdsController.rootViewController, adTypes: [.native], options: nil)
        loader.delegate = self
        adLoader = loader
        loader.load(Request())
    }

    nonisolated func adLoader(_ adLoader: AdLoader, didReceive nativeAd: NativeAd) {
        // The SDK calls delegates on the main thread.
        MainActor.assumeIsolated { self.ad = nativeAd }
    }

    nonisolated func adLoader(_ adLoader: AdLoader, didFailToReceiveAdWithError error: Error) {}
}

private struct NativeAdRepresentable: UIViewRepresentable {
    let ad: NativeAd

    func makeUIView(context: Context) -> NativeAdView {
        let view = NativeAdView()
        view.backgroundColor = UIColor(AppColor.surface)
        view.layer.cornerRadius = 20
        view.layer.cornerCurve = .continuous

        let icon = UIImageView()
        icon.layer.cornerRadius = 12
        icon.clipsToBounds = true
        icon.contentMode = .scaleAspectFill
        icon.backgroundColor = UIColor(AppColor.surfaceMuted)

        let tag = PaddedLabel()
        tag.text = L("ad_label")
        tag.font = .systemFont(ofSize: 11, weight: .bold)
        tag.textColor = UIColor(AppColor.onRibbon)
        tag.backgroundColor = UIColor(AppColor.ribbon)
        tag.layer.cornerRadius = 8
        tag.clipsToBounds = true

        let headline = UILabel()
        headline.font = .systemFont(ofSize: 15, weight: .semibold)
        headline.textColor = UIColor(AppColor.ink)

        let body = UILabel()
        body.font = .systemFont(ofSize: 13)
        body.textColor = UIColor(AppColor.inkMuted)
        body.numberOfLines = 2

        let cta = UIButton(configuration: .filled())
        cta.configuration?.baseBackgroundColor = UIColor(AppColor.accent)
        cta.configuration?.baseForegroundColor = UIColor(AppColor.onAccent)
        cta.configuration?.cornerStyle = .capsule
        cta.isUserInteractionEnabled = false // the SDK handles taps

        let texts = UIStackView(arrangedSubviews: [tag, headline, body])
        texts.axis = .vertical
        texts.alignment = .leading
        texts.spacing = 2
        let row = UIStackView(arrangedSubviews: [icon, texts, cta])
        row.axis = .horizontal
        row.alignment = .center
        row.spacing = 12
        row.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(row)
        NSLayoutConstraint.activate([
            icon.widthAnchor.constraint(equalToConstant: 48), icon.heightAnchor.constraint(equalToConstant: 48),
            row.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 14),
            row.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -14),
            row.centerYAnchor.constraint(equalTo: view.centerYAnchor),
        ])
        view.iconView = icon
        view.headlineView = headline
        view.bodyView = body
        view.callToActionView = cta
        return view
    }

    func updateUIView(_ view: NativeAdView, context: Context) {
        (view.headlineView as? UILabel)?.text = ad.headline
        (view.bodyView as? UILabel)?.text = ad.body
        (view.iconView as? UIImageView)?.image = ad.icon?.image
        view.iconView?.isHidden = ad.icon == nil
        (view.callToActionView as? UIButton)?.configuration?.title = ad.callToAction
        view.callToActionView?.isHidden = ad.callToAction == nil
        view.nativeAd = ad
    }
}

private final class PaddedLabel: UILabel {
    override func drawText(in rect: CGRect) { super.drawText(in: rect.insetBy(dx: 7, dy: 2)) }
    override var intrinsicContentSize: CGSize {
        let size = super.intrinsicContentSize
        return CGSize(width: size.width + 14, height: size.height + 4)
    }
}

// MARK: - Inline banner

/// A thin adaptive banner at the end of a long list.
struct InlineBanner: View {
    @Environment(AdsController.self) private var ads

    var body: some View {
        if ads.canShowAds {
            GeometryReader { geo in
                BannerRepresentable(unitID: ads.bannerUnitID, width: geo.size.width)
            }
            .frame(height: 60)
        }
    }
}

private struct BannerRepresentable: UIViewRepresentable {
    let unitID: String
    let width: CGFloat

    func makeUIView(context: Context) -> BannerView {
        let banner = BannerView(adSize: currentOrientationInlineAdaptiveBanner(width: max(width, 320)))
        banner.adUnitID = unitID
        banner.rootViewController = AdsController.rootViewController
        banner.load(Request())
        return banner
    }

    func updateUIView(_ banner: BannerView, context: Context) {}
}
