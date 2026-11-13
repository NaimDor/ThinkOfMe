import Foundation
import Testing
@testable import Core

@Suite struct OccasionTests {
    let calendar: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "UTC")!
        return c
    }()

    func date(_ y: Int, _ m: Int, _ d: Int) -> Date {
        calendar.date(from: DateComponents(year: y, month: m, day: d))!
    }

    @Test func countsDaysToTheNextYearlyOccurrence() {
        let birthday = Occasion(ownerId: "a", personId: "a", kind: .birthday, title: "", month: 11, day: 23)
        #expect(birthday.daysUntil(from: date(2026, 11, 11), calendar: calendar) == 12)
        #expect(birthday.daysUntil(from: date(2026, 11, 23), calendar: calendar) == 0)
        #expect(birthday.daysUntil(from: date(2026, 11, 24), calendar: calendar) == 364)
    }

    @Test func leapDayBirthdaysFallOnThe28thInCommonYears() {
        let leap = Occasion(ownerId: "a", personId: "a", kind: .birthday, title: "", month: 2, day: 29)
        let next = leap.nextDate(from: date(2027, 1, 1), calendar: calendar)!
        #expect(calendar.component(.day, from: next) == 28)
    }

    @Test func pastOneOffOccasionsHaveNoNextDate() {
        let once = Occasion(ownerId: "a", personId: "a", kind: .custom, title: "Party", month: 1, day: 1, year: 2026, yearly: false)
        #expect(once.nextDate(from: date(2026, 6, 1), calendar: calendar) == nil)
    }
}

@Suite struct DraftTests {
    @Test func allCirclesAndSpecificCirclesAreExclusive() {
        var draft = WishDraft()
        #expect(draft.visibleCircleIds == [allCircles])
        draft.toggleCircle("family")
        #expect(draft.visibleCircleIds == ["family"])
        draft.toggleCircle(allCircles)
        #expect(draft.visibleCircleIds == [allCircles])
        draft.toggleCircle(allCircles)
        #expect(draft.visibleCircleIds.isEmpty)
    }

    @Test func parsesPricesWithCommaOrDot() {
        var draft = WishDraft()
        draft.priceText = "12,50"
        #expect(draft.price?.amount == 12.5)
    }
}

@MainActor
@Suite struct DemoBackendTests {
    @Test func neverRevealsClaimsOnYourOwnWishes() async {
        let backend = DemoBackend(signedIn: true)
        var iterator = backend.claims(onWishesOf: backend.meId).makeAsyncIterator()
        let mine = await iterator.next()
        #expect(mine?.isEmpty == true)
    }

    @Test func showsClaimsOnOtherPeoplesWishes() async {
        let backend = DemoBackend(signedIn: true)
        var iterator = backend.claims(onWishesOf: "maya").makeAsyncIterator()
        let claims = await iterator.next()
        #expect(claims?["w1"]?.first?.claimerName == "Noa")
    }

    @Test func cannotClaimYourOwnWish() async {
        let backend = DemoBackend(signedIn: true)
        let mine = backend.wishList.first { $0.ownerId == backend.meId }!
        await #expect(throws: (any Error).self) { try await backend.claim(mine) }
    }

    @Test func overviewPutsTheNearestOccasionFirst() {
        let backend = DemoBackend(signedIn: true)
        let visible = backend.wishList.filter { $0.ownerId != backend.meId }
        let overview = Overview.build(me: backend.peopleById[backend.meId], people: backend.peopleById, visible: visible, occasions: backend.occasionList)
        #expect(overview.nextUp?.person?.id == "maya")
        #expect(overview.nextUp?.daysUntil == 12)
        #expect(overview.people.first?.person.id == "maya")
        #expect(overview.people.allSatisfy { $0.person.id != backend.meId })
    }
}

@Suite struct HolidayTests {
    @Test func catalogHasLunarHolidays() {
        #expect(Holidays.byId("tu_bav") != nil)
        #expect(Holidays.byId("eid_al_fitr") != nil)
    }

    @Test func defaultsDependOnLanguage() {
        #expect(Holidays.defaults(forLanguage: "he").contains("tu_bav"))
        #expect(Holidays.defaults(forLanguage: "ru").contains("womens_day"))
        #expect(!Holidays.defaults(forLanguage: "en").contains("tu_bav"))
        #expect(Holidays.defaults(forLanguage: "xx") == Holidays.defaults(forLanguage: "en"))
    }

    @Test func nextDateIsInTheFuture() {
        let next = Holidays.byId("valentines")!.nextDate(from: .now)!
        #expect(next >= Calendar.current.startOfDay(for: .now))
    }
}

@Suite struct PhotoDetailsTests {
    @Test func fillsOnlyEmptyFields() {
        var draft = WishDraft()
        draft.title = "My own title"
        let details = PhotoDetails(isProduct: true, title: "Sony WH-1000XM6", store: "KSP",
                                   price: Money(amount: 1299, currency: "ILS"), note: "Black", searchQuery: "sony wh-1000xm6")
        let merged = draft.merging(details)
        #expect(merged.title == "My own title")
        #expect(merged.priceText == "1299")
        #expect(merged.currency == "ILS")
        #expect(merged.store == "KSP")
        #expect(merged.note == "Black")
        #expect(merged.url.isEmpty)
    }

    @Test func brandAndModelStandInForTitle() {
        let merged = WishDraft().merging(PhotoDetails(isProduct: true, brand: "Hario", model: "V60"))
        #expect(merged.title == "Hario V60")
        var full = WishDraft()
        full.title = "a"; full.priceText = "1"; full.store = "b"
        #expect(!full.needsDetails)
    }
}

@Suite struct PhotoFillTests {
    @Test func clearsOnlyWhatThePhotoFilledAndTheUserKept() throws {
        var before = WishDraft()
        before.title = "Mine"
        before.currency = "USD"
        let merged = before.merging(PhotoDetails(isProduct: true, title: "Ignored", store: "KSP",
                                                 price: Money(amount: 1299, currency: "ILS"), note: "Black"))
        let fill = try #require(PhotoFill(before: before, merged: merged))
        #expect(fill.title == nil)
        var edited = merged
        edited.note = "Black, size M"
        let cleared = edited.clearing(fill)
        #expect(cleared.title == "Mine")
        #expect(cleared.priceText.isEmpty)
        #expect(cleared.currency == "USD")
        #expect(cleared.store.isEmpty)
        #expect(cleared.note == "Black, size M")
    }

    @Test func nothingFilledMeansNothingToClear() {
        var draft = WishDraft()
        draft.title = "a"; draft.priceText = "1"; draft.store = "b"
        #expect(PhotoFill(before: draft, merged: draft) == nil)
    }
}
