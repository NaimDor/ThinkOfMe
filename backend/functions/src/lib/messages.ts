/**
 * Server-side push notification copy in every launch language. Notifications
 * are rendered in the recipient's locale (stored on their device record).
 * Rule of thumb: never mention claims to a wish owner.
 */

export type Locale = "en" | "he" | "ar" | "ru";
export const SUPPORTED_LOCALES: readonly Locale[] = ["en", "he", "ar", "ru"];

export function resolveLocale(raw: string | undefined | null): Locale {
  const base = (raw ?? "en").toLowerCase().split(/[-_]/)[0] ?? "en";
  if (base === "iw") return "he"; // legacy Java code for Hebrew
  return (SUPPORTED_LOCALES as readonly string[]).includes(base) ? (base as Locale) : "en";
}

export interface Message {
  title: string;
  body: string;
}

type Vars = Record<string, string | number>;

function fill(template: string, vars: Vars): string {
  return template.replace(/\{(\w+)\}/g, (_, key: string) => String(vars[key] ?? ""));
}

const TEMPLATES = {
  selfUpcoming: {
    en: { title: "Your {occasion} is in {days} days", body: "Your people will be looking soon. Add a few wishes?" },
    he: { title: "{occasion} שלך בעוד {days} ימים", body: "האנשים שלך יציצו בקרוב. אולי תוסיפו כמה משאלות?" },
    ar: { title: "مناسبتك ({occasion}) بعد {days} يوم", body: "سيبحث أحبّاؤك قريبًا. هل تضيف بعض الأمنيات؟" },
    ru: { title: "{occasion} через {days} дн.", body: "Близкие скоро заглянут в ваш список. Добавите пару желаний?" },
  },
  othersUpcoming: {
    en: { title: "{name}'s {occasion} is in {days} days", body: "Take a look at the list. Some wishes are still open." },
    he: { title: "{occasion} של {name} בעוד {days} ימים", body: "הציצו ברשימה. יש עוד משאלות פנויות." },
    ar: { title: "{occasion} {name} بعد {days} يوم", body: "ألقِ نظرة على القائمة. ما زالت هناك أمنيات متاحة." },
    ru: { title: "{occasion}: {name} — через {days} дн.", body: "Загляните в список — там ещё есть свободные желания." },
  },
  othersToday: {
    en: { title: "It's {name}'s {occasion} today", body: "Send some love." },
    he: { title: "היום {occasion} של {name}", body: "זה הזמן לשלוח קצת אהבה." },
    ar: { title: "اليوم {occasion} {name}", body: "أرسل بعض الحب." },
    ru: { title: "{name}: {occasion} — сегодня!", body: "Самое время поздравить." },
  },
  claimedWishChanged: {
    en: { title: "A wish you're getting was updated", body: "{name} changed \"{wish}\". Take a look before you buy." },
    he: { title: "משאלה שאתם קונים עודכנה", body: "{name} שינו את \"{wish}\". כדאי להציץ לפני הקנייה." },
    ar: { title: "تم تعديل أمنية ستشتريها", body: "عدّل {name} \"{wish}\". ألقِ نظرة قبل الشراء." },
    ru: { title: "Желание, которое вы дарите, изменилось", body: "{name} изменил(а) «{wish}». Проверьте перед покупкой." },
  },
  claimedWishRemoved: {
    en: { title: "A wish you're getting was removed", body: "{name} removed \"{wish}\" from their list." },
    he: { title: "משאלה שאתם קונים הוסרה", body: "{name} הסירו את \"{wish}\" מהרשימה." },
    ar: { title: "تمت إزالة أمنية ستشتريها", body: "أزال {name} \"{wish}\" من قائمته." },
    ru: { title: "Желание, которое вы дарите, удалено", body: "{name} удалил(а) «{wish}» из списка." },
  },
} satisfies Record<string, Record<Locale, Message>>;

export type MessageKey = keyof typeof TEMPLATES;

const OCCASION_NAMES: Record<string, Record<Locale, string>> = {
  birthday: { en: "birthday", he: "יום ההולדת", ar: "عيد ميلاد", ru: "День рождения" },
  anniversary: { en: "anniversary", he: "יום הנישואין", ar: "ذكرى زواج", ru: "Годовщина" },
  holiday: { en: "holiday", he: "החג", ar: "عيد", ru: "Праздник" },
  custom: { en: "special day", he: "היום המיוחד", ar: "يوم مميّز", ru: "Особый день" },
};

export function occasionName(kind: string, locale: Locale): string {
  return OCCASION_NAMES[kind]?.[locale] ?? OCCASION_NAMES.custom![locale];
}

export function render(key: MessageKey, locale: Locale, vars: Vars = {}): Message {
  const template = TEMPLATES[key][locale];
  return { title: fill(template.title, vars), body: fill(template.body, vars) };
}
