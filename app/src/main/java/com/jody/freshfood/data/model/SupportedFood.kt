package com.jody.freshfood.data.model

/**
 * Data class representing a food item with its supported ripeness states.
 * Used to display detection capabilities in the Settings screen.
 *
 * @property fruitName The normalized fruit name (e.g., "Apples", "Bananas", "Oranges")
 * @property ripenessStates List of supported ripeness states (e.g., ["fresh", "rotten", "unripe"])
 */
data class SupportedFood(
    val fruitName: String,
    val ripenessStates: List<String>
)
