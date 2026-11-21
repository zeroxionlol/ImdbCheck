# IMDb Check (Android)

Eigene App im Stil von seriesgraph.com: Filme und Serien suchen, IMDb-Wertungen sehen und eine eigene Liste führen. Alle Daten liegen nur auf dem Handy.

## Installieren / Aktualisieren
1. Auf dem Handy unter **Releases** die neueste `ImdbCheck-1.0.x.apk` herunterladen.
2. Datei öffnen und „Installieren“ tippen (beim ersten Mal die Installation aus dieser Quelle erlauben).
3. Updates einfach über die alte Version installieren – die Liste bleibt erhalten.

## Funktionen
- Suche mit Vorschlägen ab 3 Buchstaben (Poster, Jahr, Film/Serie, Darsteller)
- Detailseite mit IMDb-Wertung, Stimmen, Genres, Handlung
- Serien: Episoden-Heatmap (Staffeln × Folgen, Farbskala), Verlaufsgraph mit Trendlinie je Staffel, Staffel-Durchschnitt, Top- und Flop-Folgen
- Meine Liste: IMDb-Wertung, Filter (Filme/Serien), Sortierung, „Gesehen“-Haken, automatische Aktualisierung alle 12 Stunden
- Datensicherung als JSON (Export/Import)

## Datenquellen
- IMDb-Suchvorschläge (`v3.sg.media-imdb.com`)
- IMDb-GraphQL (`api.graphql.imdb.com`) für Wertungen und Episoden
- optional OMDb (eigener kostenloser Key in den Einstellungen) als Ersatzquelle

## Build
Jeder Push auf `main` baut per GitHub Actions eine signierte Release-APK (`.github/workflows/build.yml`) und veröffentlicht sie als Release `v1.0.<Lauf-Nr.>`.
