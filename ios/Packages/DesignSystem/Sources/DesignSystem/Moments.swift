import SwiftUI

/// The "wrap it" moment. When `isWrapped` turns true the stage lifts and tilts
/// in 3D, a gold ribbon wraps it, the bow springs on and sparkles burst, with
/// haptics. At rest the stage is flat; a wrapped stage keeps its ribbon.
public struct WrapStage<Content: View>: View {
    let isWrapped: Bool
    let content: Content

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.layoutDirection) private var layoutDirection
    @State private var vertical: CGFloat
    @State private var horizontal: CGFloat
    @State private var bow: CGFloat
    @State private var tilt: Double = 0
    @State private var lift: CGFloat = 1
    @State private var burst = 0

    public init(isWrapped: Bool, @ViewBuilder content: () -> Content) {
        self.isWrapped = isWrapped
        self.content = content()
        _vertical = State(initialValue: isWrapped ? 1 : 0)
        _horizontal = State(initialValue: isWrapped ? 1 : 0)
        _bow = State(initialValue: isWrapped ? 1 : 0)
    }

    private static var ribbon: LinearGradient {
        LinearGradient(colors: [Color(hex: 0xC98E2F), Color(hex: 0xF9DE9C), Color(hex: 0xF2C46D), Color(hex: 0xD79B3A)], startPoint: .leading, endPoint: .trailing)
    }

    public var body: some View {
        ZStack {
            content
            Rectangle()
                .fill(Self.ribbon)
                .frame(width: 32)
                .scaleEffect(x: 1, y: vertical, anchor: .top)
            Rectangle()
                .fill(LinearGradient(colors: [Color(hex: 0xC98E2F), Color(hex: 0xF9DE9C), Color(hex: 0xF2C46D), Color(hex: 0xD79B3A)], startPoint: .top, endPoint: .bottom))
                .frame(height: 32)
                .scaleEffect(x: horizontal, y: 1, anchor: layoutDirection == .rightToLeft ? .trailing : .leading)
            BowShape()
                .fill(LinearGradient(colors: [Color(hex: 0xFBE3A8), Color(hex: 0xF2C46D), Color(hex: 0xC98E2F)], startPoint: .topLeading, endPoint: .bottomTrailing))
                .frame(width: 116, height: 68)
                .offset(y: -22)
                .scaleEffect(bow)
                .rotationEffect(.degrees(Double(1 - bow) * -25))
            SparkleBurst(trigger: burst)
        }
        .scaleEffect(lift)
        .rotation3DEffect(.degrees(tilt), axis: (x: 1, y: -0.5, z: 0), perspective: 0.6)
        .sensoryFeedback(.success, trigger: burst)
        .onChange(of: isWrapped) { _, wrapped in animate(wrapped) }
        .accessibilityElement(children: .contain)
    }

    private func animate(_ wrapped: Bool) {
        if reduceMotion {
            withAnimation(.easeInOut(duration: 0.2)) {
                vertical = wrapped ? 1 : 0; horizontal = wrapped ? 1 : 0; bow = wrapped ? 1 : 0
            }
            return
        }
        guard wrapped else {
            withAnimation(.easeIn(duration: 0.18)) { bow = 0 }
            withAnimation(.easeInOut(duration: 0.26)) { horizontal = 0 }
            withAnimation(.easeInOut(duration: 0.26).delay(0.12)) { vertical = 0 }
            return
        }
        // Tilt back, overshoot forward, settle flat.
        withAnimation(.easeOut(duration: 0.3)) { tilt = 16; lift = 0.95 }
        withAnimation(.easeInOut(duration: 0.35).delay(0.3)) { tilt = -5; lift = 1.02 }
        withAnimation(.spring(response: 0.4, dampingFraction: 0.7).delay(0.65)) { tilt = 0; lift = 1 }
        withAnimation(.easeInOut(duration: 0.5).delay(0.15)) { vertical = 1 }
        withAnimation(.easeInOut(duration: 0.5).delay(0.35)) { horizontal = 1 }
        withAnimation(.spring(response: 0.45, dampingFraction: 0.45).delay(0.7)) { bow = 1 }
        Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(820))
            burst += 1
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

struct SparkleBurst: View {
    let trigger: Int
    @State private var progress: CGFloat = 0

    var body: some View {
        ZStack {
            ForEach(0..<6, id: \.self) { index in
                let angle = Angle.degrees(-140 + Double(index) * 52)
                Image(systemName: "sparkle")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(index % 3 == 2 ? AppColor.accent : Color(hex: 0xE3A94A))
                    .offset(x: cos(angle.radians) * 120 * progress, y: sin(angle.radians) * 120 * progress)
                    .rotationEffect(.degrees(90 * progress))
                    .scaleEffect(progress)
                    .opacity(progress < 0.7 ? 1 : (1 - (progress - 0.7) / 0.3))
            }
        }
        .allowsHitTesting(false)
        .opacity(trigger == 0 ? 0 : 1)
        .onChange(of: trigger) {
            progress = 0
            withAnimation(.easeOut(duration: 0.9)) { progress = 1 }
        }
        .accessibilityHidden(true)
    }
}

/// A small gift box that floats and turns in 3D: used for empty states,
/// onboarding and sign in.
public struct GiftBoxMoment: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    public init() {}

    public var body: some View {
        TimelineView(.animation(paused: reduceMotion)) { context in
            let t = context.date.timeIntervalSinceReferenceDate
            GeometryReader { geo in
                let w = geo.size.width, h = geo.size.height
                ZStack {
                    Ellipse().fill(.black.opacity(0.08)).frame(width: w * 0.56, height: h * 0.08).position(x: w / 2, y: h * 0.9)
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
                    .rotation3DEffect(.degrees(reduceMotion ? 0 : sin(t * 0.9) * 22), axis: (x: 0, y: 1, z: 0), perspective: 0.5)
                    .offset(y: reduceMotion ? 0 : sin(t * 2.6) * 4)
                }
            }
        }
        .accessibilityHidden(true)
    }
}
