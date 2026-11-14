import Foundation

/// The demo world's names and texts in each app language, so previews, demo builds and
/// store screenshots read naturally. Prices are in shekels outside English.
struct DemoContent {
    let language: String

    init(language: String) {
        let code = String(language.prefix(2)).lowercased()
        self.language = ["he", "iw", "ar", "ru"].contains(code) ? (code == "iw" ? "he" : code) : "en"
    }

    var currency: String { language == "en" ? "USD" : "ILS" }

    /// The English price, or a rounded shekel price.
    func price(_ usd: Double) -> Money {
        guard language != "en" else { return Money(amount: usd, currency: "USD") }
        let ils = usd * 3.65
        let rounded = ils < 100 ? (ils / 5).rounded() * 5 : (ils / 10).rounded() * 10
        return Money(amount: rounded, currency: "ILS")
    }

    func text(_ key: String) -> String {
        Self.table[key]?[language] ?? Self.table[key]?["en"] ?? key
    }

    private static let table: [String: [String: String]] = [
        "me": ["en": "Dana", "he": "דנה", "ar": "لينا", "ru": "Даша"],
        "maya": ["en": "Maya", "he": "מאיה", "ar": "مايا", "ru": "Маша"],
        "mom": ["en": "Mom", "he": "אמא", "ar": "ماما", "ru": "Мама"],
        "dan": ["en": "Dan", "he": "דן", "ar": "سامي", "ru": "Дима"],
        "noa": ["en": "Noa", "he": "נועה", "ar": "نور", "ru": "Аня"],
        "circle.us": ["en": "Maya & me", "he": "מאיה ואני", "ar": "مايا وأنا", "ru": "Маша и я"],
        "circle.family": ["en": "Family", "he": "משפחה", "ar": "العائلة", "ru": "Семья"],
        "birthday": ["en": "Birthday", "he": "יום הולדת", "ar": "عيد ميلاد", "ru": "День рождения"],
        "anniversary": ["en": "Mom & Dad's anniversary", "he": "יום הנישואין של אמא ואבא", "ar": "ذكرى زواج ماما وبابا", "ru": "Годовщина мамы и папы"],
        "colors.me": ["en": "Forest green, cream", "he": "ירוק יער, קרם", "ar": "أخضر غامق، كريمي", "ru": "Тёмно-зелёный, кремовый"],
        "colors.maya": ["en": "Black, terracotta", "he": "שחור, טרקוטה", "ar": "أسود، تيراكوتا", "ru": "Чёрный, терракота"],
        "dontBuy.maya": ["en": "Candles, please, I have enough", "he": "בלי נרות בבקשה, יש לי מספיק", "ar": "لا شموع من فضلكم، لديّ ما يكفي", "ru": "Только не свечи, их у меня хватает"],
        "allergies.mom": ["en": "Lilies", "he": "ליליות", "ar": "الزنابق", "ru": "Лилии"],
        "w1": ["en": "Noise-cancelling headphones", "he": "אוזניות מבטלות רעשים", "ar": "سماعات بخاصية إلغاء الضوضاء", "ru": "Наушники с шумоподавлением"],
        "w1.note": ["en": "Matte black please, for flights and deep-work days.", "he": "שחור מט בבקשה, לטיסות ולימי עבודה מרוכזת.", "ar": "باللون الأسود المطفأ من فضلك، للسفر وأيام العمل المركّز.", "ru": "Матово-чёрные, пожалуйста, для перелётов и работы."],
        "w1.store": ["en": "Soundhouse", "he": "סאונדהאוס", "ar": "ساوند هاوس", "ru": "Саундхаус"],
        "w2": ["en": "Ceramics class for two", "he": "סדנת קרמיקה לשניים", "ar": "ورشة خزف لشخصين", "ru": "Мастер-класс по керамике для двоих"],
        "w2.note": ["en": "Any weekend works. Let's go together!", "he": "כל סוף שבוע מתאים. נלך יחד!", "ar": "أي عطلة نهاية أسبوع تناسبني. لنذهب معًا!", "ru": "Подойдут любые выходные. Пойдём вместе!"],
        "w3": ["en": "Italian cookbook", "he": "ספר בישול איטלקי", "ar": "كتاب طبخ إيطالي", "ru": "Книга итальянской кухни"],
        "w3.store": ["en": "Bookshop", "he": "חנות הספרים", "ar": "المكتبة", "ru": "Книжный"],
        "w4": ["en": "Silk scarf, terracotta", "he": "צעיף משי בצבע טרקוטה", "ar": "وشاح حرير بلون التيراكوتا", "ru": "Шёлковый шарф, терракота"],
        "w4.store": ["en": "Atelier", "he": "אטלייה", "ar": "أتيليه", "ru": "Ателье"],
        "w5": ["en": "Running socks (3 pairs)", "he": "גרבי ריצה (3 זוגות)", "ar": "جوارب جري (3 أزواج)", "ru": "Беговые носки (3 пары)"],
        "w6": ["en": "Large monstera plant", "he": "צמח מונסטרה גדול", "ar": "نبتة مونستيرا كبيرة", "ru": "Большая монстера"],
        "w6.note": ["en": "In a ceramic pot if possible.", "he": "בעציץ קרמיקה אם אפשר.", "ar": "في أصيص خزفي إن أمكن.", "ru": "Если можно, в керамическом горшке."],
        "w6.store": ["en": "Green House", "he": "גרין האוס", "ar": "جرين هاوس", "ru": "Грин Хаус"],
        "w7": ["en": "Instant film camera", "he": "מצלמת פילם מיידית", "ar": "كاميرا فورية", "ru": "Камера моментальной печати"],
        "w7.store": ["en": "PhotoLab", "he": "פוטולאב", "ar": "فوتو لاب", "ru": "ФотоЛаб"],
        "w8": ["en": "Climbing gym pass", "he": "כרטיסייה לקיר טיפוס", "ar": "اشتراك في صالة التسلق", "ru": "Абонемент на скалодром"],
        "w9": ["en": "Linen bedding set", "he": "סט מצעי פשתן", "ar": "طقم أغطية سرير من الكتان", "ru": "Комплект льняного белья"],
        "w9.note": ["en": "Sage green, queen size.", "he": "ירוק מרווה, מיטה זוגית.", "ar": "أخضر مريمي، مقاس كوين.", "ru": "Шалфейно-зелёный, двуспальный."],
        "w9.store": ["en": "Linen Co.", "he": "לינן", "ar": "لينن", "ru": "Лён и Ко"],
        "w10": ["en": "Pour-over coffee kit", "he": "ערכת חליטת קפה", "ar": "عدة تحضير القهوة بالتقطير", "ru": "Набор для заваривания кофе"],
        "w11": ["en": "Weekend in the mountains", "he": "סוף שבוע בצפון", "ar": "عطلة نهاية أسبوع في الجبال", "ru": "Выходные в горах"],
        "w11.note": ["en": "Anywhere with a fireplace.", "he": "כל מקום עם אח.", "ar": "أي مكان فيه مدفأة.", "ru": "Где угодно, лишь бы с камином."],
    ]
}
