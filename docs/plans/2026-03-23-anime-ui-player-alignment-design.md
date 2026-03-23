# Anime UI Player Alignment Design

## Goal

Make anime feel like a native part of Mihon instead of a separate experimental feature.

## Decisions

- Anime browse should use the same visual language as normal source browsing.
- Anime series details should follow the manga details screen rhythm:
  header, action row, expandable description, chapter-like episode list, and a start/resume FAB.
- Torrent release selection stays on the series screen, but choosing a release opens the in-app player immediately.
- File selection, subtitle selection, stop, and buffering feedback belong inside the player.
- Player buffering should not hide the whole image once playback is already visible.
- The player should rotate automatically and behave like a normal media screen.

## Scope

- Rework `AnimeDetailsScreen` to use manga-like info and list presentation.
- Rework `AnimePlayerScreen` to be a proper full-screen overlay player.
- Keep the torrent backend flow intact while cleaning up UX.

## Non-goals

- Full anime tracking parity with manga in this step.
- Replacing the torrent backend implementation itself.
