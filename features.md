# LovelyFrontier — Features List

This document lists all the features implemented in the **LovelyFrontier** plugin, mapping out their backend mechanisms and player-facing functionalities.

---

## 1. Instance Lifecycle & State Machine
- **Atomic State Transitions**: Safely guides dungeon instances through 4 distinct stages: `LOADING → ACTIVE → COMPLETING → CLEANUP`.
- **Automatic Recovery**: On server startup (`onEnable()`), automatically detects and unloads zombie worlds, deletes left-over directories, and updates their database status to prevent corrupted states.
- **Graceful Shutdown**: On server stop/reload (`onDisable()`), teleports players to safety, unloads Multiverse worlds, cancels tasks, and marks all active instances as cleaned up in the database.

## 2. Ticket & Entry Management
- **Universal vs. Specific Tickets**: Configurable option (`universal_ticket: true/false`) to run a single unified ticket tier across all dungeons, or distinct tickets for each dungeon.
- **Consume-on-Entry Guarantee**: Prevents ticket loss due to GUI errors or party vote cancellations; tickets are only deducted at the exact moment of player teleportation.
- **Deadlock-Resistant Database Operations**: Wrap all ticket exchanges in retry transactions to prevent MySQL deadlock crashes (error code 1213).
- **Idempotency Keys**: Generates UUID-based idempotency keys on session creation, preventing double-ticketing or multiple instance triggers from network latency.

## 3. Party & Matchmaking Flow
- **MMOCore Party Integration**: Coordinates dungeon entry permissions with party leaders.
- **Party Voting**: Initiates a 60-second difficulty selection vote upon portal activation.
- **Party Confirmation**: A 180-second readiness confirmation phase checks player state, ticket possession, and playtime gates before launching the dungeon.
- **Reward Scaling**: Dynamically adjusts reward values based on party size (e.g., Solo scaling: `× 0.70`, Duo: `× 1.00`, Trio: `× 1.10`).

## 4. Loot & Shuffling System
- **Chest Types**: Supports `MAIN` chests (boss reward, guaranteed for all) and `BONUS` chests (exploration rewards scattered around the map).
- **Shuffle Algorithm**: Implements a Fisher-Yates shuffle over weighted item lists, applying difficulty multipliers and party scaling factors.
- **Main-Thread Chest Filling**: To prevent async world-edit crashes, chest inventory injection is delayed by 1 tick on the Bukkit scheduler.
- **Chest Protection**: Prevents breaking or stealing chests inside active dungeon instances.

## 5. Anti-Abuse & Alt Detection
- **Playtime Gate**: Validates total playtime (`total_playtime_h`) before allowing players to buy tickets, receive free ticket rewards, or claim completion loot.
- **IP Hash Alt Check**: On join, player IPs are hashed (using SHA-256 for privacy) and checked against database records. If the count of accounts sharing the IP exceeds `max_accounts_per_ip`, the player is flagged, locking them out of ticket purchases and rewards.
- **Scroll Rate Limiting**: Restricts the use of personal portal scrolls via daily limits, maximum stack sizes, and cooldowns.

## 6. Weekly Free Tickets
- **Automated Check**: Scheduled task (`WeeklyTicketTask`) checks online players, confirms eligibility based on playtime gates/alt flags, and grants tickets once every 7 days.
- **Visual Feedback**: Plays level-up sounds and sends congratulatory messages upon ticket acquisition.

## 7. Command Filtering
- **Command Whitelist**: Enforces a strict whitelist (e.g. `/lf`, `/lfa`, `/party`) inside active dungeons using `PlayerCommandPreprocessEvent` to block player escape or cheat commands.

## 8. Death & Spectator Mode
- **No-Drop Death Handling**: Lethal damage is intercepted, player health/potion status is restored, and they are transitioned to `GameMode.SPECTATOR` immediately.
- **Movement Restrictions**: Spectators are restricted to a 150-block radius from the dungeon spawn and cannot use spectator teleport commands to spy out of bounds.

## 9. Reconnection Grace & Dynamic Boss Scaling
- **5-Minute Grace Period**: Players who disconnect during a dungeon have 5 minutes to reconnect and return to the instance before their slot is freed.
- **Dynamic Boss HP Scaling**: Adjusts active boss Max HP and current HP when players disconnect or reconnect.
- **Mail Delivery on Expiry**: If a player disconnected but their team completes the dungeon, the player's share of completion rewards is automatically sent to their mail.

## 10. Mail Delivery System
- **Asynchronous Processing**: Queries, delivers, and claims mail items asynchronously, preventing lag.
- **ItemStack Serialization**: Serializes items safely using Base64 YAML strings.
- **Command GUI Interface**: Offers `/lf mail` for players to view and claim their loot.

## 11. World Portal Spawns
- **Dynamic Portals**: Automatically builds beacon pyramids with colored glass tiers based on config conditions.
- **Proximity Guidance**: Displays action-bar or chat prompts naming required key items when a player approaches a portal.
