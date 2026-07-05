package com.example.saaraapp

/**
 * Detects duplicate reminders and picks the higher-quality one.
 *
 * Two reminders are considered duplicates if:
 *   - They share the same reminderDate (or both have no date)
 *   - They have at least one overlapping tag
 *   - Their messages share ≥30% of words
 *
 * Quality scoring prefers:
 *   - More English words (ASCII ratio)
 *   - Longer, more descriptive messages
 *   - Proper capitalisation and punctuation
 *   - Explicit time mentions (e.g. "5pm", "11:30 AM")
 */
object ReminderDeduplicator {

    // ── Similarity ────────────────────────────────────────────────────────────

    /**
     * Returns true if [incoming] is about the same thing as [existing].
     * Uses date match + tag overlap + word overlap.
     */
    fun isSimilar(existing: ReminderItem, incoming: ReminderItem): Boolean {
        // Dates must match (both null counts as a match)
        if (existing.reminderDate != incoming.reminderDate) return false

        // At least one shared tag (case-insensitive)
        val existingTags = existing.tags.map { it.lowercase() }.toSet()
        val incomingTags = incoming.tags.map { it.lowercase() }.toSet()
        val sharedTags   = existingTags.intersect(incomingTags)
        if (sharedTags.isEmpty()) return false

        // ≥30% word overlap between the two messages
        val existingWords = tokenize(existing.originalMessage)
        val incomingWords = tokenize(incoming.originalMessage)
        val overlap = existingWords.intersect(incomingWords).size.toFloat() /
                      minOf(existingWords.size, incomingWords.size).coerceAtLeast(1).toFloat()

        return overlap >= 0.30f
    }

    // ── Quality scoring ───────────────────────────────────────────────────────

    /**
     * Scores a message for quality. Higher = better.
     *
     * Criteria:
     *   +50  English (ASCII) letter ratio
     *   +20  message length (capped)
     *   +10  starts with a capital letter
     *   +10  ends with punctuation
     *   +10  contains an explicit time (e.g. "5pm", "11:30 AM")
     */
    fun qualityScore(message: String): Float {
        var score = 0f

        // English ratio — ASCII letters vs all letters
        val letters      = message.filter { it.isLetter() }
        val asciiLetters = letters.filter { it.code < 128 }
        if (letters.isNotEmpty()) {
            score += (asciiLetters.length.toFloat() / letters.length) * 50f
        }

        // Length bonus (up to 20 points)
        score += minOf(message.length / 5f, 20f)

        // Starts with capital
        if (message.firstOrNull()?.isUpperCase() == true) score += 10f

        // Ends with punctuation
        if (message.lastOrNull { !it.isWhitespace() }.let { it == '.' || it == '!' || it == '?' }) {
            score += 10f
        }

        // Explicit time present (5pm, 11:30am, 3 PM, etc.)
        val timeRegex = Regex("""\d{1,2}(:\d{2})?\s*(am|pm|AM|PM)""")
        if (timeRegex.containsMatchIn(message)) score += 10f

        return score
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun tokenize(text: String): Set<String> =
        text.lowercase()
            .split(Regex("\\s+|[,।.!?]"))
            .map { it.trim() }
            .filter { it.length > 2 }   // ignore tiny words like "hi", "ok"
            .toSet()
}
