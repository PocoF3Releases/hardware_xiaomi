# Appearance and equalizer UI

System Settings appearance is the default. Settings > Dossier theme enables a
persisted app-only alternative: charcoal/ivory surfaces, static distressed
background, diagonal accents, condensed headings, numbered categories, stepped
top rules, left rules and red panel footers. It follows system light/dark mode.
Audio profiles, engine state and reset operations do not change appearance.

The equalizer fits all 20 bands to the available width without horizontal
scrolling. Vertical labels identify frequencies; the selected-band editor gives
precise gain adjustment and previous/next navigation. Dossier gains are red
above zero and gray at/below zero. The plotted curve uses actual preset gains.

Validation, 2026-09-20: app resources and Dolby Kotlin/Compose passed targeted
compilation. The redesigned app was installed for iteration; supplied device screenshots
show both themes, all 20 bands, banners and the corrected title bar. Stock surface,
slider contrast and heading refinements are included in ca2c95c. This is not an
exhaustive accessibility, large-font, landscape or route-transition validation.

The main-page mastheads use the supplied PNG artwork in drawable-nodpi, fitted
to their intrinsic aspect ratio without cropping or re-creating the lettering.

Approved transparent mastheads are stored in drawable-nodpi. The centered header
uses available width and intrinsic aspect ratio, capped at 220 dp high for the
illustrated banner and 68 dp for the standard logo. ContentScale.Fit prevents
cropping. Standard logo tint follows onSurface for light/dark contrast. PNG
alpha was verified; Kotlin/Compose and AAPT2 checks passed after integration.

## XiaomiParts alignment

The system appearance uses the same platform surface tokens and variable title/body fonts as XiaomiParts. Dossier remains an independent optional appearance. Profile, intelligent EQ, speaker tuning, graphic preset and new-profile base choices use relevant leading 24 dp icons in a 40 dp slot. A primary-container circle indicates the selected option; unselected symbols remain unfilled. Rows preserve radio selection semantics and immediate choice/dismiss behavior. Native base IDs determine profile icons, and user-created EQ presets use a neutral tuning icon. Icon provenance and complete licenses are in `licenses/`.

Manage games uses rounded search, installed app artwork and bounded labels; unavailable stock packages show one package label rather than repeating it. Game tuning uses compact tonal cards, integer value badges and a scrollable body so action buttons remain available on smaller displays. Dense 0–100 sliders round to native integers without rendering a tick for every value; short discrete ranges retain their ticks. Cancel and device-default editing remain local until confirmation.

Standard appearance uses a compact 16 sp setting title / 14 sp description hierarchy with scalable line heights. Section labels use 14 sp; normal touch targets retain Material minimum sizes. Edit/reset dialogs share a titleLarge heading and a 560 dp maximum width with 24 dp outer margins. Text fields and long dialog bodies scroll; the compact equalizer scrolls as one page. Segmented selection highlights follow the measured row height rather than assuming a fixed text height.
