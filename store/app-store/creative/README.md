# App Store creative assets

Optional artwork shown at the top of the product page (**header**, 21:9, 3840 x 1646) and in
**search results** (3:2, 3840 x 2560), one per language. Upload in App Store Connect → the app →
App Store → Creative assets. They go through App Review with the version (or as a standalone
submission later). Requires iOS 27; older devices show the screenshots instead.

`art.html` draws them: the app-icon gift (`gift.svg`, the icon without its background) and the
subtitle as two lines inside Apple's art safe area, with wrapped gifts and sparkles around it.
Taglines are in the `T` table in `art.html`. Rebuild with `node render.js` (output in `out/`).
