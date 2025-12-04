package com.nostalgiapipe.utils

import java.util.Optional

/**
 * Converts a Java Optional<T> to a Kotlin nullable T?.
 */
fun <T> Optional<T>.toNullable(): T? = orElse(null)
