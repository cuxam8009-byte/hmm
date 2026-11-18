# Dymith — Minimal Start Page Pass

## Scope

Replace the previous dashboard completely with the user-supplied monochrome clock/arc/omnibox concept. Keep Rust, JNI, URL resolver, ad-block and the external-page WebView behavior unchanged.

## Performance decisions

- No UI framework, no remote JS/CSS, no background hero image, no news feed.
- The start page is one self-contained HTML document in `StartPage.kt`.
- Favicon images are lazy/async decoded and degrade to a one-letter fallback.
- Clock updates once at the next minute boundary; timer pauses while the document is hidden.
- Short entrance animation only; respects reduced-motion preference.
- Native address toolbar is hidden on the start page because the HTML dock is the address/search field; it returns on external pages.
- `onWebScrolled` ignores the start page so the native toolbar cannot accidentally slide over the custom dock.
- Native navigation bridge accepts only HTTP(S) URLs with a host.
- Removed the previous RSS feed worker from the app source/startup path.

## Validation performed in this workspace

- Extracted inline JavaScript and ran `node --check` successfully.
- Confirmed start page no longer contains old dashboard news-card/gradient markup.
- Confirmed bottom dock and address input are present.
- ZIP/source inspection completed.

## Not validated here

No Android SDK/NDK/Gradle installation is available in this workspace, so APK compilation, WebView behavior, touch/IME behavior and frame-time performance still require CI and an HONOR MT6765 device run.
