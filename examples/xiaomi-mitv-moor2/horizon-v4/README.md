# Horizon v4 (the home screen's light): v3 with nothing in the sky

Parth, 23 Oct 2026: "the exact same specifications, but without any solid objects in the background,
like stars or moon or anything like that, because that is hampering readability".

v4 is [Horizon v3](../horizon-v3/README.md) as it is on the TV (480×270, the 4.5:1 readability floor,
grain, the feed's `sky`), with these removed:

- the 909 stars and their list (`res/raw/stars.txt`);
- the Milky Way;
- meteors (never built).

Kept, because they are light and colour, not shapes: the continuous mood colours, the glow where the
real sun is, the moon's light (a cooler, slightly brighter sky on its side, by phase; no disc), the
weather's clouds, haze and storm, the grain and the readability floor.

`mockup.html` shows each frame as v4, v3 and v2, with the faintest text's contrast measured on each.
