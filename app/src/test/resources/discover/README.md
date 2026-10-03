# Discover parser fixtures

Source: the public guest pages at https://ddys.app/discover/, observed on 2026-10-03.
These samples retain only the form choices, result count, cards, and pagination needed by the parser.
They contain no access cookies, tokens, scripts, or account data.

- `page.html`: unfiltered first page, including category/region/genre choices and the Top250 tag.
- `filtered.html`: movie, United States, science fiction, Douban score at least 8.
- `range.html`: the same filters plus adventure and years 2010–2020, with all tags required.
- `page2.html`: unfiltered second page.

The captured titles and counts describe the fixture date, not the site's current catalogue.
