import SwiftUI

/// The "wrap it" moment. When `isWrapped` turns true the stage lifts toward you and
/// rocks in 3D on one continuous curve, a gold ribbon sweeps around it, the bow drops
/// on with a springy wobble, a glint runs along the ribbon and sparkles burst in two
/// waves, with haptics. At rest the stage is flat; a wrapped stage keeps its ribbon.
public struct WrapStage<Content: View>: View {
    let isWrapped: Bool
    let content: Content

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.layoutDirection) private var layoutDirection
    @State private var vertical: CGFloat
    @State private var horizontal: CGFloat
    @State private var bow: CGFloat
    @State private var shine: CGFloat = 0
    /// When the rocking started; nil at rest.
    @State private var rockStart: Date?
    /// When the sparkles started; nil at rest.
    @State private var burstStart: Date?
    @State private var bursts = 0
    @State private var taps = 0

    public init(isWrapped: Bool, @ViewBuilder content: () -> Content) {
        self.isWrapped = isWrapped
        self.content = content()
        _vertical = State(initialValue: isWrapped ? 1 : 0)
        _horizontal = State(initialValue: isWrapped ? 1 : 0)
        _bow = State(initialValue: isWrapped ? 1 : 0)
    }

    private var rockDuration: Double { 1.25 }
    private var ribbonColors: [Color] { [Color(hex: 0xC98E2F), Color(hex: 0xF9DE9C), Color(hex: 0xF2C46D), Color(hex: 0xD79B3A)] }

    public var body: some View {
        TimelineView(.animation(paused: rockStart == nil)) { context in
            // One continuous curve: rock back, swing forward a little, settle flat (a damped sine).
            let t = rockProgress(at: context.date)
            let rise = sin(Double.pi * t)
            let tilt = 30 * sin(2 * Double.pi * t) * (1 - t) * (1 - t).squareRoot()
            stage
                .scaleEffect(1 + 0.055 * rise)
                .offset(y: -10 * rise)
                .rotation3DEffect(.degrees(tilt), axis: (x: 1, y: -0.55, z: 0), perspective: 0.6)
        }
        .overlay {
            SparkleBurst(start: burstStart)
                .frame(width: 440, height: 440)
                .offset(y: -22)
        }
        .sensoryFeedback(.impact(weight: .light), trigger: taps)
        .sensoryFeedback(.success, trigger: bursts)
        .onChange(of: isWrapped) { _, wrapped in animate(wrapped) }
        .accessibilityElement(children: .contain)
    }

    private var stage: some View {
        ZStack {
            content
            Rectangle()
                .fill(LinearGradient(colors: ribbonColors, startPoint: .leading, endPoint: .trailing))
                .frame(width: 32)
                .overlay { glint(alongHeight: true) }
                .scaleEffect(x: 1, y: vertical, anchor: .top)
            Rectangle()
                .fill(LinearGradient(colors: ribbonColors, startPoint: .top, endPoint: .bottom))
                .frame(height: 32)
                .overlay { glint(alongHeight: false) }
                .scaleEffect(x: horizontal, y: 1, anchor: layoutDirection == .rightToLeft ? .trailing : .leading)
            BowShape()
                .fill(LinearGradient(colors: [Color(hex: 0xFBE3A8), Color(hex: 0xF2C46D), Color(hex: 0xC98E2F)], startPoint: .topLeading, endPoint: .bottomTrailing))
                .frame(width: 124, height: 72)
                .scaleEffect(bow)
                .rotationEffect(.degrees(Double(1 - bow) * -32))
                .offset(y: -22 - 36 * (1 - min(bow, 1)))
                .opacity(Double(min(bow * 3, 1)))
        }
    }

    /// A band of light that travels along a ribbon once. It rests off either end, out of sight.
    private func glint(alongHeight: Bool) -> some View {
        GeometryReader { geo in
            let length: CGFloat = 120
            let travel = (alongHeight ? geo.size.height : geo.size.width) + length
            LinearGradient(
                colors: [.clear, .white.opacity(0.75), .clear],
                startPoint: alongHeight ? .top : .leading, endPoint: alongHeight ? .bottom : .trailing
            )
            .frame(width: alongHeight ? geo.size.width : length, height: alongHeight ? length : geo.size.height)
            .frame(width: geo.size.width, height: geo.size.height)
            .offset(x: alongHeight ? 0 : (shine - 0.5) * travel, y: alongHeight ? (shine - 0.5) * travel : 0)
        }
        .clipped()
        .allowsHitTesting(false)
    }

    private func rockProgress(at date: Date) -> Double {
        guard let rockStart else { return 0 }
        return min(max(date.timeIntervalSince(rockStart) / rockDuration, 0), 1)
    }

    private func animate(_ wrapped: Bool) {
        if reduceMotion {
            withAnimation(.easeInOut(duration: 0.2)) {
                vertical = wrapped ? 1 : 0; horizontal = wrapped ? 1 : 0; bow = wrapped ? 1 : 0
            }
            return
        }
        guard wrapped else {
            shine = 0
            withAnimation(.easeInOut(duration: 0.24)) { bow = 0 }
            withAnimation(.timingCurve(0.5, 0, 0.15, 1, duration: 0.34).delay(0.06)) { horizontal = 0 }
            withAnimation(.timingCurve(0.5, 0, 0.15, 1, duration: 0.34).delay(0.16)) { vertical = 0 }
            return
        }
        taps += 1
        shine = 0
        rockStart = .now
        // The ribbon is pulled round with a slow start and a long glide.
        withAnimation(.timingCurve(0.5, 0, 0.15, 1, duration: 0.62).delay(0.12)) { vertical = 1 }
        withAnimation(.timingCurve(0.5, 0, 0.15, 1, duration: 0.62).delay(0.30)) { horizontal = 1 }
        withAnimation(.spring(response: 0.55, dampingFraction: 0.36).delay(0.72)) { bow = 1 }
        withAnimation(.easeInOut(duration: 0.9).delay(0.98)) { shine = 1 }
        Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(840))
            bursts += 1
            burstStart = .now
            try? await Task.sleep(for: .milliseconds(1700))
            rockStart = nil
            burstStart = nil
        }
    }
}

/// Two loops, a knot and tails, in a 128×74 design space.
struct BowShape: Shape {
    func path(in rect: CGRect) -> Path {
        let sx = rect.width / 128, sy = rect.height / 74
        func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: rect.minX + x * sx, y: rect.minY + y * sy) }
        var path = Path()
        path.move(to: p(58, 52)); path.addLine(to: p(44, 74)); path.addLine(to: p(56, 74)); path.addLine(to: p(64, 62))
        path.addLine(to: p(72, 74)); path.addLine(to: p(84, 74)); path.addLine(to: p(70, 52)); path.closeSubpath()
        path.move(to: p(64, 50))
        path.addCurve(to: p(10, 38), control1: p(40, 8), control2: p(6, 10))
        path.addCurve(to: p(64, 50), control1: p(13, 58), control2: p(44, 58))
        path.move(to: p(64, 50))
        path.addCurve(to: p(118, 38), control1: p(88, 8), control2: p(122, 10))
        path.addCurve(to: p(64, 50), control1: p(115, 58), control2: p(84, 58))
        path.addEllipse(in: CGRect(origin: p(53, 38), size: CGSize(width: 22 * sx, height: 20 * sy)))
        return path
    }
}

/// A four-point sparkle around the origin.
func sparklePath(radius: CGFloat) -> Path {
    let pinch = radius * 0.22
    var path = Path()
    path.move(to: CGPoint(x: 0, y: -radius))
    path.addQuadCurve(to: CGPoint(x: radius, y: 0), control: CGPoint(x: pinch, y: -pinch))
    path.addQuadCurve(to: CGPoint(x: 0, y: radius), control: CGPoint(x: pinch, y: pinch))
    path.addQuadCurve(to: CGPoint(x: -radius, y: 0), control: CGPoint(x: -pinch, y: pinch))
    path.addQuadCurve(to: CGPoint(x: 0, y: -radius), control: CGPoint(x: -pinch, y: -pinch))
    path.closeSubpath()
    return path
}

/// Two waves of sparkles flying out from the bow, with a soft ring of light.
struct SparkleBurst: View {
    let start: Date?

    private struct Spark {
        let angle: Double, distance: Double, size: Double, delay: Double, color: Int
    }

    // A wide first wave, then a closer, slower second one.
    private static let sparks: [Spark] =
        (0..<9).map { i in Spark(angle: (-90 + Double(i) * 40 + Double(i % 2) * 9) * .pi / 180, distance: 150 + Double(i % 3) * 22, size: 9 + Double(i % 3) * 3, delay: 0, color: i % 4) }
        + (0..<7).map { i in Spark(angle: (-64 + Double(i) * 51) * .pi / 180, distance: 86 + Double(i % 2) * 18, size: 6 + Double(i % 2) * 3, delay: 0.16, color: (i + 1) % 4) }

    var body: some View {
        let palette = [Color(hex: 0xE3A94A), Color(hex: 0xF9DE9C), AppColor.accent, Color.white]
        let sparks = Self.sparks
        return TimelineView(.animation(paused: start == nil)) { context in
            let p = start.map { context.date.timeIntervalSince($0) / 1.5 } ?? 0
            Canvas { ctx, size in
                guard p > 0, p < 1 else { return }
                let center = CGPoint(x: size.width / 2, y: size.height / 2)
                // Ring of light.
                let ring = min(p / 0.55, 1)
                let ringEase = 1 - (1 - ring) * (1 - ring)
                let radius = 30 + 150 * ringEase
                ctx.stroke(
                    Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)),
                    with: .color(Color(hex: 0xF9DE9C).opacity(0.55 * (1 - ring))),
                    lineWidth: 10 * (1 - ring) + 1
                )
                for spark in sparks {
                    let local = min(max((p - spark.delay) / (1 - spark.delay), 0), 1)
                    guard local > 0 else { continue }
                    let out = 1 - pow(1 - local, 3)
                    let x = cos(spark.angle) * spark.distance * out
                    let y = sin(spark.angle) * spark.distance * out + 46 * local * local
                    let grow = min(local / 0.18, 1)
                    let fade = local < 0.55 ? 1 : 1 - (local - 0.55) / 0.45
                    let transform = CGAffineTransform(translationX: center.x + x, y: center.y + y).rotated(by: 140 * local * .pi / 180)
                    ctx.fill(
                        sparklePath(radius: spark.size * grow * (0.7 + 0.3 * fade)).applying(transform),
                        with: .color(palette[spark.color].opacity(fade))
                    )
                }
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

/// A small gift box that floats, turns in 3D and twinkles: used for empty states,
/// onboarding and sign in.
public struct GiftBoxMoment: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    public init() {}

    public var body: some View {
        TimelineView(.animation(paused: reduceMotion)) { context in
            // One looping phase drives everything, so the turn, the float and the twinkles stay in step.
            let phase = context.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 5.6) / 5.6
            let turn = reduceMotion ? 0 : sin(2 * Double.pi * phase)
            let float = reduceMotion ? 0 : sin(4 * Double.pi * phase)
            GeometryReader { geo in
                let w = geo.size.width, h = geo.size.height
                ZStack {
                    // The shadow stays on the ground and tightens as the box rises.
                    Ellipse().fill(.black.opacity(0.10 - 0.03 * float))
                        .frame(width: w * 0.56 * (1 - 0.16 * float), height: h * 0.075)
                        .position(x: w / 2, y: h * 0.905)
                    ZStack {
                        RoundedRectangle(cornerRadius: w * 0.05).fill(AppColor.accent)
                            .frame(width: w * 0.52, height: h * 0.4).position(x: w / 2, y: h * 0.62)
                        RoundedRectangle(cornerRadius: w * 0.04).fill(AppColor.accent.opacity(0.85))
                            .frame(width: w * 0.62, height: h * 0.13).position(x: w / 2, y: h * 0.385)
                        Rectangle().fill(AppColor.ribbon)
                            .frame(width: w * 0.08, height: h * 0.5).position(x: w / 2, y: h * 0.57)
                        BowShape().fill(Color(hex: 0xE3A94A))
                            .frame(width: w * 0.5, height: h * 0.28).position(x: w / 2, y: h * 0.25)
                    }
                    .scaleEffect(x: 1, y: 1 + 0.018 * float)
                    .rotationEffect(.degrees(turn * -3))
                    .rotation3DEffect(.degrees(turn * 26), axis: (x: 0, y: 1, z: 0), perspective: 0.5)
                    .offset(y: -float * 7 - 2)
                    // Twinkles around the box, each on its own beat.
                    twinkle(phase: phase, offset: 0.00).position(x: w * 0.16, y: h * 0.24)
                    twinkle(phase: phase, offset: 0.37).position(x: w * 0.86, y: h * 0.36)
                    twinkle(phase: phase, offset: 0.68).position(x: w * 0.78, y: h * 0.10)
                }
                .frame(width: w, height: h)
            }
        }
        .accessibilityHidden(true)
    }

    private func twinkle(phase: Double, offset: Double) -> some View {
        let beat = reduceMotion ? 0.5 : (phase * 2 + offset).truncatingRemainder(dividingBy: 1)
        let glow = sin(Double.pi * beat)
        return Image(systemName: "sparkle")
            .font(.system(size: 13, weight: .bold))
            .foregroundStyle(Color(hex: 0xE3A94A))
            .scaleEffect(0.4 + 0.6 * glow)
            .rotationEffect(.degrees(45 * beat))
            .opacity(glow)
    }
}
