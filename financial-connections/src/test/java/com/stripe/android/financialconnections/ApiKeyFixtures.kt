package com.stripe.android.financialconnections

import com.stripe.android.financialconnections.model.FinancialConnectionsAccountList
import com.stripe.android.financialconnections.model.FinancialConnectionsAuthorizationSession
import com.stripe.android.financialconnections.model.FinancialConnectionsSession
import com.stripe.android.financialconnections.model.FinancialConnectionsSessionManifest
import com.stripe.android.financialconnections.model.PartnerAccountsList
import com.stripe.android.financialconnections.model.SynchronizeSessionResponse

internal object ApiKeyFixtures {
    const val DEFAULT_PUBLISHABLE_KEY = "pk_test_vOo1umqsYxSrP5UXfOeL3ecm"
    const val DEFAULT_FINANCIAL_CONNECTIONS_SESSION_SECRET = "las_client_secret_asdf1234"

    const val HOSTED_AUTH_URL = "https://stripe.com/auth/flow/start"
    const val SUCCESS_URL = "stripe-auth://link-accounts/success"
    const val CANCEL_URL = "stripe-auth://link-accounts/cancel"

    fun syncResponse() = SynchronizeSessionResponse(
        manifest = sessionManifest(),
        text = null
    )

    fun financialConnectionsSessionNoAccounts() = FinancialConnectionsSession(
        clientSecret = "las_1234567890",
        id = DEFAULT_FINANCIAL_CONNECTIONS_SESSION_SECRET,
        accountsNew = FinancialConnectionsAccountList(
            data = emptyList(),
            hasMore = false,
            url = "url",
            count = 0
        ),
        livemode = true
    )

    fun sessionManifest() = FinancialConnectionsSessionManifest(
        allowManualEntry = true,
        consentRequired = true,
        customManualEntryHandling = true,
        disableLinkMoreAccounts = true,
        id = "1234",
        instantVerificationDisabled = true,
        institutionSearchDisabled = true,
        livemode = true,
        manualEntryUsesMicrodeposits = true,
        mobileHandoffEnabled = true,
        nextPane = FinancialConnectionsSessionManifest.Pane.CONSENT,
        permissions = emptyList(),
        product = FinancialConnectionsSessionManifest.Product.STRIPE_CARD,
        singleAccount = true,
        useSingleSortSearch = true,
        successUrl = SUCCESS_URL,
        cancelUrl = CANCEL_URL,
        hostedAuthUrl = HOSTED_AUTH_URL
    )

    fun authorizationSession() = FinancialConnectionsAuthorizationSession(
        id = "id",
        nextPane = FinancialConnectionsSessionManifest.Pane.CONSENT,
        flow = FinancialConnectionsAuthorizationSession.Flow.MX_OAUTH,
        institutionSkipAccountSelection = null,
        showPartnerDisclosure = null,
        skipAccountSelection = null,
        url = null,
        urlQrCode = null,
        _isOAuth = true
    )

    fun partnerAccountList() = PartnerAccountsList(
        data = emptyList(),
        hasMore = false,
        nextPane = FinancialConnectionsSessionManifest.Pane.CONSENT,
        url = ""
    )
}
