import SwiftUI

// MARK: - Buttons

/// Presses shrink slightly with a spring: tactile without heavy chrome.
public struct PressableStyle: ButtonStyle {
    public init() {}
    public func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.7), value: configuration.isPressed)
    }
}

public enum AppButtonKind { case primary, secondary, ribbon }

public struct AppButton: View {
    let title: String
    let systemImage: String?
    let kind: AppButtonKind
    let isLoading: Bool
    let action: () -> Void
    @Environment(\.isEnabled) private var isEnabled

    public init(_ title: String, systemImage: String? = nil, kind: AppButtonKind = .primary, isLoading: Bool = false, action: @escaping () -> Void) {
        self.title = title
        self.systemImage = systemImage
        self.kind = kind
        self.isLoading = isLoading
        self.action = action
    }

    private var background: Color {
        switch kind {
        case .primary: AppColor.accent
        case .secondary: AppColor.surfaceMuted
        case .ribbon: AppColor.ribbon
        }
    }

    private var foreground: Color {
        switch kind {
        case .primary: AppColor.onAccent
        case .secondary: AppColor.ink
        case .ribbon: AppColor.onRibbon
        }
    }

    public var body: some View {
        Button(action: action) {
            HStack(spacing: Space.xs) {
                if isLoading {
                    ProgressView().tint(foreground)
                } else {
                    if let systemImage { Image(systemName: systemImage).font(.body.weight(.semibold)) }
                    Text(title).textStyle(.bodyStrong).lineLimit(1)
                }
            }
            .foregroundStyle(foreground)
            .frame(maxWidth: .infinity, minHeight: 56)
            .padding(.horizontal, Space.l)
            .background(background.opacity(isEnabled ? 1 : 0.4), in: RoundedRectangle(cornerRadius: Radius.m, style: .continuous))
        }
        .buttonStyle(PressableStyle())
        .disabled(isLoading)
    }
}

/// Small filled pill, e.g. "View list".
public struct PillButton: View {
    let title: String
    let action: () -> Void
    public init(_ title: String, action: @escaping () -> Void) { self.title = title; self.action = action }
    public var body: some View {
        Button(action: action) {
            Text(title).textStyle(.callout).fontWeight(.semibold)
                .foregroundStyle(AppColor.onAccent)
                .padding(.horizontal, Space.m).padding(.vertical, 10)
                .background(AppColor.accent, in: Capsule())
        }
        .buttonStyle(PressableStyle())
    }
}

public struct CircleIconButton: View {
    let systemImage: String
    let label: String
    let action: () -> Void
    public init(_ systemImage: String, label: String, action: @escaping () -> Void) {
        self.systemImage = systemImage; self.label = label; self.action = action
    }
    public var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(AppColor.ink)
                .frame(width: 44, height: 44)
                .background(AppColor.surfaceMuted, in: Circle())
        }
        .buttonStyle(PressableStyle())
        .accessibilityLabel(label)
    }
}

// MARK: - Chips

public struct ChoiceChip: View {
    let title: String
    let systemImage: String?
    let isSelected: Bool
    let action: () -> Void

    public init(_ title: String, systemImage: String? = nil, isSelected: Bool, action: @escaping () -> Void) {
        self.title = title; self.systemImage = systemImage; self.isSelected = isSelected; self.action = action
    }

    public var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if let systemImage { Image(systemName: systemImage).font(.caption.weight(.semibold)) }
                Text(title).textStyle(.callout)
            }
            .foregroundStyle(isSelected ? AppColor.canvas : AppColor.ink)
            .padding(.horizontal, 14).padding(.vertical, 10)
            .background(isSelected ? AppColor.ink : AppColor.surface, in: Capsule())
            .overlay(Capsule().strokeBorder(isSelected ? .clear : AppColor.hairline))
            .animation(.appSpring, value: isSelected)
        }
        .buttonStyle(PressableStyle())
        .accessibilityAddTraits(isSelected ? .isSelected : [])
        .sensoryFeedback(.selection, trigger: isSelected)
    }
}

public struct Tag: View {
    let title: String
    let gold: Bool
    public init(_ title: String, gold: Bool = false) { self.title = title; self.gold = gold }
    public var body: some View {
        Text(title)
            .textStyle(.tab).fontWeight(.bold)
            .foregroundStyle(gold ? AppColor.onRibbon : AppColor.ink)
            .padding(.horizontal, 9).padding(.vertical, 4)
            .background(gold ? AppColor.ribbon : AppColor.surface.opacity(0.9), in: Capsule())
    }
}

// MARK: - Avatars

public struct InitialAvatar: View {
    let initial: String
    let hueIndex: Int
    let size: CGFloat
    public init(initial: String, hueIndex: Int, size: CGFloat = 46) {
        self.initial = initial; self.hueIndex = hueIndex; self.size = size
    }
    public var body: some View {
        Circle()
            .fill(PersonHue.color(hueIndex))
            .frame(width: size, height: size)
            .overlay(Text(initial).font(.system(size: size * 0.37, weight: .semibold)).foregroundStyle(.white))
            .accessibilityHidden(true)
    }
}

// MARK: - Countdown ring

/// Days-left ring that draws in on appear; a full ring means today.
public struct CountdownRing: View {
    let days: Int
    let label: String
    let window: Int
    let size: CGFloat
    @State private var progress: CGFloat = 0

    public init(days: Int, label: String, window: Int = 30, size: CGFloat = 76) {
        self.days = days; self.label = label; self.window = window; self.size = size
    }

    private var target: CGFloat {
        max(0.04, 1 - CGFloat(min(max(days, 0), window)) / CGFloat(window))
    }

    public var body: some View {
        ZStack {
            Circle().stroke(AppColor.accentTrack, lineWidth: 4)
            Circle()
                .trim(from: 0, to: progress)
                .stroke(AppColor.accent, style: StrokeStyle(lineWidth: 4, lineCap: .round))
                .rotationEffect(.degrees(-90))
            VStack(spacing: 0) {
                Text("\(days)").font(AppFonts.display(size: 24, relativeTo: .title2)).foregroundStyle(AppColor.ink)
                Text(label).textStyle(.tab).foregroundStyle(AppColor.inkMuted)
            }
        }
        .frame(width: size, height: size)
        .onAppear { withAnimation(.easeOut(duration: 1.2)) { progress = target } }
        .onChange(of: days) { withAnimation(.easeOut(duration: 0.6)) { progress = target } }
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Layout pieces

public struct SectionHeader<Action: View>: View {
    let title: String
    let action: Action
    public init(_ title: String, @ViewBuilder action: () -> Action = { EmptyView() }) {
        self.title = title; self.action = action()
    }
    public var body: some View {
        HStack {
            Text(title).textStyle(.title).foregroundStyle(AppColor.ink)
            Spacer()
            action
        }
        .padding(.bottom, Space.xs)
    }
}

public struct Hairline: View {
    public init() {}
    public var body: some View { Rectangle().fill(AppColor.hairline).frame(height: 1) }
}

/// Calm empty state with a small 3D gift moment.
public struct EmptyStateView<Action: View>: View {
    let title: String
    let message: String
    let action: Action
    public init(_ title: String, message: String, @ViewBuilder action: () -> Action = { EmptyView() }) {
        self.title = title; self.message = message; self.action = action()
    }
    public var body: some View {
        VStack(spacing: Space.xs) {
            GiftBoxMoment().frame(width: 96, height: 96)
            Text(title).textStyle(.title).foregroundStyle(AppColor.ink).multilineTextAlignment(.center)
            Text(message).textStyle(.callout).foregroundStyle(AppColor.inkMuted).multilineTextAlignment(.center)
            action.padding(.top, Space.s)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Space.xxl)
        .padding(.horizontal, Space.l)
    }
}

/// Quiet text field: label above, filled surface, hairline border.
public struct AppTextField: View {
    let label: String
    let placeholder: String
    @Binding var text: String
    let axis: Axis

    public init(_ label: String, text: Binding<String>, placeholder: String = "", axis: Axis = .horizontal) {
        self.label = label; self._text = text; self.placeholder = placeholder; self.axis = axis
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).textStyle(.caption).fontWeight(.semibold).foregroundStyle(AppColor.inkMuted)
            TextField(placeholder, text: $text, axis: axis)
                .textStyle(.body)
                .foregroundStyle(AppColor.ink)
                .lineLimit(axis == .vertical ? 3...8 : 1...1)
                .padding(14)
                .frame(minHeight: 52)
                .background(AppColor.surface, in: RoundedRectangle(cornerRadius: Radius.s, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: Radius.s, style: .continuous).strokeBorder(AppColor.hairline))
                .accessibilityLabel(label)
        }
    }
}
