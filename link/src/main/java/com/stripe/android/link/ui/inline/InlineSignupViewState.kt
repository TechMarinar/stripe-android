package com.stripe.android.link.ui.inline

import androidx.annotation.RestrictTo
import com.stripe.android.link.ui.signup.SignUpState

/**
 * The LinkInlineSignup view state.
 *
 * @param userInput The collected input from the user, always valid unless null.
 * @param isExpanded Whether the checkbox is selected and the view is expanded.
 * @param apiFailed Whether an API call has failed. In such cases, we want to continue the
 *                  payment flow without Link.
 * @param signUpState The stage of the sign in or sign up flow.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
data class InlineSignupViewState internal constructor(
    val userInput: UserInput?,
    val merchantName: String,
    internal val isExpanded: Boolean,
    internal val apiFailed: Boolean,
    internal val signUpState: SignUpState
) {
    /**
     * Whether the view is active and the payment should be processed through Link.
     */
    val useLink = isExpanded && !apiFailed
}
