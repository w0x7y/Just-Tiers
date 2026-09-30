# Just-Tiers

Just-Tiers displays placements from independent Minecraft PvP tier sites. Settings
control nametag presentation; an explicit player lookup shows every known placement.

## Language

**Source**: A tier site with its own gamemodes, placements and availability. One
source's answer does not establish a player's standing on another source.
_Avoid_: Provider, leaderboard service

**Tier**: A player's placement in one source's gamemode, including high/low division
and whether the placement is retired.
_Avoid_: Score, level

**Lookup session**: One attempt to identify a player and collect the sources' answers.
Retrying starts another session, which may reuse successful answers.
_Avoid_: Search job

**Lookup result**: One source's displayed answer within a lookup session, including
whether its request has settled and the freshness of its displayed placements. A later
session does not replace this answer.
_Avoid_: Live cache row

**Unranked**: A source answered successfully without a listed placement. An unavailable
source provides no evidence of whether the player is ranked.
_Avoid_: Missing, failed lookup

**Active settings**: The settings currently used by the mod. A command change stays
active for the session even when saving it fails.
_Avoid_: Saved settings

**Settings draft**: Editable screen settings that become active only after a successful
save. Cancelling or failing to save a draft preserves the active settings.
_Avoid_: Live config

**Minecraft target**: A supported game version with its own installable mod artifact
and declared dependency requirements.
_Avoid_: Universal build
