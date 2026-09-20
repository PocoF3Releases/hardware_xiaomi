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

Validation, 2026-09-20: app resources compiled and linked against existing build
dependencies; all Dolby Kotlin compiled with the Compose plugin and a 2 GB heap.
MiSound's changed Java class and resources also compiled. No full ROM build or
installation was performed for this revision. Visual and route-transition
checks belong to the next installed build: theme persistence, both appearances,
all bands at normal/large font sizes, back navigation, and headset plug/unplug.

The main-page mastheads use the supplied PNG artwork in drawable-nodpi, fitted
to their intrinsic aspect ratio without cropping or re-creating the lettering.

Approved transparent mastheads are stored in drawable-nodpi. The centered header
uses available width and intrinsic aspect ratio, capped at 220 dp high for the
illustrated banner and 88 dp for the standard logo. ContentScale.Fit prevents
cropping. Standard logo tint follows onSurface for light/dark contrast. PNG
alpha was verified; Kotlin/Compose and AAPT2 checks passed after integration.
