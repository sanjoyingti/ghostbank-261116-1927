=== ABOUT: Formats paise into readable rupee amounts with Indian digit grouping ===
package com.arin.ghostbank.ui.components

import java.text.DecimalFormat

private val wholeFormat = DecimalFormat("#,##,##0")
private val exactFormat = DecimalFormat("#,##,##0.00")

fun rupees(paise: Long): String = "₹" + wholeFormat.format(paise / 100)
fun rupeesExact(paise: Long): String = "₹" + exactFormat.format(paise / 100.0)
fun paiseLine(paise: Long): String = "$paise paise"
