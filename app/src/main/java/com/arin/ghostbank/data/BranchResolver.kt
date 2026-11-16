package com.arin.ghostbank.data

import com.arin.ghostbank.model.Banks

data class BranchInfo(val bankName: String, val branchLabel: String)

/**
 * IFSC is 4 letters + 0 + 6 characters. The first four letters identify the bank; the last six
 * identify the branch. We resolve the bank locally (no lookup service) and show the branch code.
 */
object BranchResolver {

    fun resolve(ifsc: String): BranchInfo? {
        val clean = ifsc.trim().uppercase()
        if (!Banks.isValidIfsc(clean)) return null
        val bank = Banks.byIfsc(clean) ?: return null
        val branchCode = clean.takeLast(6)
        return BranchInfo(bank.name, "branch $branchCode")
    }

    /** A light plausibility check used before any network call. */
    fun formatHint(ifsc: String): String? = when {
        ifsc.isBlank() -> null
        ifsc.length < 11 -> "IFSC needs 11 characters — ${11 - ifsc.length} to go"
        !Banks.isValidIfsc(ifsc) -> "Shape must be 4 letters, a 0, then 6 letters or digits"
        Banks.byIfsc(ifsc) == null -> "Valid shape, but not one of the 27 supported banks"
        else -> null
    }
}
