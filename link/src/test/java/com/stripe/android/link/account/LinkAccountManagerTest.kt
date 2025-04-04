package com.stripe.android.link.account

import com.google.common.truth.Truth.assertThat
import com.stripe.android.core.StripeError
import com.stripe.android.core.exception.AuthenticationException
import com.stripe.android.link.LinkPaymentLauncher
import com.stripe.android.link.analytics.LinkEventsReporter
import com.stripe.android.link.model.AccountStatus
import com.stripe.android.link.repositories.LinkRepository
import com.stripe.android.link.ui.inline.UserInput
import com.stripe.android.model.ConsumerSession
import com.stripe.android.model.ConsumerSessionLookup
import com.stripe.android.model.ConsumerSignUpConsentAction
import com.stripe.android.model.StripeIntent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.Mockito.times
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class LinkAccountManagerTest {
    private val linkRepository = mock<LinkRepository>()
    private val linkEventsReporter = mock<LinkEventsReporter>()
    private val cookieStore = mock<CookieStore>().apply {
        whenever(getAuthSessionCookie()).thenReturn("cookie")
    }

    private val verifiedSession = mock<ConsumerSession.VerificationSession>().apply {
        whenever(type).thenReturn(ConsumerSession.VerificationSession.SessionType.Sms)
        whenever(state).thenReturn(ConsumerSession.VerificationSession.SessionState.Verified)
    }
    private val mockConsumerSession = mock<ConsumerSession>().apply {
        whenever(clientSecret).thenReturn(CLIENT_SECRET)
        whenever(verificationSessions).thenReturn(listOf(verifiedSession))
        whenever(publishableKey).thenReturn(PUBLISHABLE_KEY)
    }

    @Test
    fun `When auth cookie exists then it is used at start`() = runSuspendTest {
        val cookie = "cookie"
        whenever(cookieStore.getAuthSessionCookie()).thenReturn(cookie)
        whenever(cookieStore.getNewUserEmail()).thenReturn("email")

        assertThat(accountManager(EMAIL).accountStatus.first()).isEqualTo(AccountStatus.Verified)

        verify(linkRepository).lookupConsumer(isNull(), eq(cookie))
    }

    @Test
    fun `When cookie exists and network call fails then account status is Error`() = runSuspendTest {
        whenever(cookieStore.getAuthSessionCookie()).thenReturn("cookie")
        whenever(linkRepository.lookupConsumer(anyOrNull(), anyOrNull()))
            .thenReturn(Result.failure(Exception()))

        assertThat(accountManager(EMAIL).accountStatus.first()).isEqualTo(AccountStatus.Error)
    }

    @Test
    fun `When new user email exists then it is used at start`() = runSuspendTest {
        val email = "email"
        whenever(cookieStore.getAuthSessionCookie()).thenReturn(null)
        whenever(cookieStore.getNewUserEmail()).thenReturn(email)

        assertThat(accountManager(EMAIL).accountStatus.first()).isEqualTo(AccountStatus.Verified)

        verify(linkRepository).lookupConsumer(eq(email), isNull())
    }

    @Test
    fun `When new user email exists and network call fails then account status is Error`() = runSuspendTest {
        val email = "email"
        whenever(cookieStore.getAuthSessionCookie()).thenReturn(null)
        whenever(cookieStore.getNewUserEmail()).thenReturn(email)
        whenever(linkRepository.lookupConsumer(anyOrNull(), anyOrNull()))
            .thenReturn(Result.failure(Exception()))

        assertThat(accountManager(EMAIL).accountStatus.first()).isEqualTo(AccountStatus.Error)
    }

    @Test
    fun `When customerEmail is set in arguments then it is looked up`() = runSuspendTest {
        whenever(cookieStore.getAuthSessionCookie()).thenReturn(null)

        assertThat(accountManager(EMAIL).accountStatus.first()).isEqualTo(AccountStatus.Verified)

        verify(linkRepository).lookupConsumer(EMAIL, null)
    }

    @Test
    fun `When customerEmail has signed out then it is not looked up`() = runSuspendTest {
        whenever(cookieStore.getAuthSessionCookie()).thenReturn(null)
        whenever(cookieStore.isEmailLoggedOut(EMAIL)).thenReturn(true)

        assertThat(accountManager(EMAIL).accountStatus.first()).isEqualTo(AccountStatus.SignedOut)

        verifyNoInteractions(linkRepository)
    }

    @Test
    fun `When customerEmail is set and network call fails then account status is Error`() = runSuspendTest {
        whenever(cookieStore.getAuthSessionCookie()).thenReturn(null)
        whenever(linkRepository.lookupConsumer(anyOrNull(), anyOrNull()))
            .thenReturn(Result.failure(Exception()))

        assertThat(accountManager(EMAIL).accountStatus.first()).isEqualTo(AccountStatus.Error)
    }

    @Test
    fun `When ConsumerSession contains consumerPublishableKey then key is updated`() {
        val accountManager = accountManager()

        assertThat(accountManager.consumerPublishableKey).isNull()

        accountManager.setAccountNullable(mockConsumerSession)

        assertThat(accountManager.consumerPublishableKey).isEqualTo(PUBLISHABLE_KEY)
    }

    @Test
    fun `When ConsumerSession is updated with the same email then consumerPublishableKey is kept`() {
        val accountManager = accountManager()

        accountManager.setAccountNullable(mockConsumerSession)

        assertThat(accountManager.consumerPublishableKey).isEqualTo(PUBLISHABLE_KEY)

        whenever(mockConsumerSession.publishableKey).thenReturn(null)
        accountManager.setAccountNullable(mockConsumerSession)

        assertThat(accountManager.consumerPublishableKey).isEqualTo(PUBLISHABLE_KEY)
    }

    @Test
    fun `When ConsumerSession is updated with different email then consumerPublishableKey is removed`() {
        val accountManager = accountManager()

        accountManager.setAccountNullable(mockConsumerSession)

        assertThat(accountManager.consumerPublishableKey).isEqualTo(PUBLISHABLE_KEY)

        whenever(mockConsumerSession.publishableKey).thenReturn(null)
        whenever(mockConsumerSession.emailAddress).thenReturn("different@email.com")
        accountManager.setAccountNullable(mockConsumerSession)

        assertThat(accountManager.consumerPublishableKey).isNull()
    }

    @Test
    fun `lookupConsumer starts session when startSession is true`() = runSuspendTest {
        mockUnverifiedAccountLookup()

        val accountManager = accountManager()

        accountManager.lookupConsumer(EMAIL, true)

        verify(linkRepository).startVerification(anyOrNull(), anyOrNull(), anyOrNull())
        assertThat(accountManager.linkAccount.value).isNotNull()
    }

    @Test
    fun `lookupConsumer does not start session when startSession is false`() = runSuspendTest {
        val accountManager = accountManager()

        accountManager.lookupConsumer(EMAIL, false)

        verify(linkRepository, times(0)).startVerification(anyOrNull(), anyOrNull(), anyOrNull())
        assertThat(accountManager.linkAccount.value).isNull()
    }

    @Test
    fun `lookupConsumer sends analytics event when call fails`() = runSuspendTest {
        whenever(linkRepository.lookupConsumer(anyOrNull(), anyOrNull()))
            .thenReturn(Result.failure(Exception()))

        accountManager().lookupConsumer(EMAIL, false)

        verify(linkEventsReporter).onAccountLookupFailure()
    }

    @Test
    fun `When cookie is invalid it is deleted after consumer lookup`() = runSuspendTest {
        mockNonexistentAccountLookup()
        val accountManager = accountManager()

        accountManager.lookupConsumer(null)

        verify(cookieStore).updateAuthSessionCookie("")
    }

    @Test
    fun `signInWithUserInput sends correct parameters and starts session for existing user`() =
        runSuspendTest {
            val accountManager = accountManager()

            accountManager.signInWithUserInput(UserInput.SignIn(EMAIL))

            verify(linkRepository).lookupConsumer(eq(EMAIL), anyOrNull())
            assertThat(accountManager.linkAccount.value).isNotNull()
        }

    @Test
    fun `signInWithUserInput sends correct parameters and starts session for new user`() =
        runSuspendTest {
            val accountManager = accountManager()
            val phone = "phone"
            val country = "country"
            val name = "name"

            accountManager.signInWithUserInput(UserInput.SignUp(EMAIL, phone, country, name))

            verify(linkRepository).consumerSignUp(
                email = eq(EMAIL),
                phone = eq(phone),
                country = eq(country),
                name = eq(name),
                authSessionCookie = anyOrNull(),
                consentAction = eq(ConsumerSignUpConsentAction.Checkbox)
            )
            assertThat(accountManager.linkAccount.value).isNotNull()
        }

    @Test
    fun `signInWithUserInput for new user sends analytics event when call succeeds`() =
        runSuspendTest {
            accountManager().signInWithUserInput(
                UserInput.SignUp(
                    email = EMAIL,
                    phone = "phone",
                    country = "country",
                    name = "name"
                )
            )

            verify(linkEventsReporter).onSignupCompleted(true)
        }

    @Test
    fun `signInWithUserInput for new user sends analytics event when call fails`() =
        runSuspendTest {
            whenever(
                linkRepository.consumerSignUp(
                    email = anyOrNull(),
                    phone = anyOrNull(),
                    country = anyOrNull(),
                    name = anyOrNull(),
                    authSessionCookie = anyOrNull(),
                    consentAction = anyOrNull()
                )
            ).thenReturn(Result.failure(Exception()))

            accountManager().signInWithUserInput(
                UserInput.SignUp(
                    email = EMAIL,
                    phone = "phone",
                    country = "country",
                    name = "name"
                )
            )

            verify(linkEventsReporter).onSignupFailure(true)
        }

    @Test
    fun `signUp stores email when successfully signed up`() = runSuspendTest {
        val accountManager = accountManager()

        accountManager.signUp(EMAIL, "phone", "US", "name", ConsumerSignUpConsentAction.Checkbox)

        verify(cookieStore).storeNewUserEmail(EMAIL)
    }

    @Test
    fun `startVerification updates account`() = runSuspendTest {
        val accountManager = accountManager()
        accountManager.setAccountNullable(mockConsumerSession)

        accountManager.startVerification()

        assertThat(accountManager.linkAccount.value).isNotNull()
    }

    @Test
    fun `startVerification retries on auth error`() = runSuspendTest {
        val accountManager = accountManager()
        accountManager.setAccountNullable(mockConsumerSession)

        whenever(linkRepository.startVerification(anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(
                Result.failure(AuthenticationException(StripeError())),
                Result.success(mockConsumerSession)
            )

        accountManager.startVerification()

        verify(linkRepository, times(2)).startVerification(anyOrNull(), anyOrNull(), anyOrNull())
        verify(linkRepository).lookupConsumer(anyOrNull(), anyOrNull())

        assertThat(accountManager.linkAccount.value).isNotNull()
    }

    @Test
    fun `startVerification does not retry on auth error if no cookie exists`() = runSuspendTest {
        whenever(cookieStore.getAuthSessionCookie()).thenReturn(null)
        val accountManager = accountManager()
        accountManager.setAccountNullable(mockConsumerSession)

        whenever(linkRepository.startVerification(anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(
                Result.failure(AuthenticationException(StripeError())),
                Result.success(mockConsumerSession)
            )

        accountManager.startVerification()

        verify(linkRepository).startVerification(anyOrNull(), anyOrNull(), anyOrNull())
        verify(linkRepository, times(0)).lookupConsumer(anyOrNull(), anyOrNull())
    }

    @Test
    fun `startVerification uses consumerPublishableKey`() = runSuspendTest {
        val accountManager = accountManager()
        accountManager.setAccountNullable(mockConsumerSession)

        accountManager.startVerification()

        val keyCaptor = argumentCaptor<String>()
        verify(linkRepository).startVerification(anyOrNull(), keyCaptor.capture(), anyOrNull())
        assertThat(keyCaptor.firstValue).isEqualTo(PUBLISHABLE_KEY)
    }

    @Test
    fun `startVerification sends analytics event when call fails`() = runSuspendTest {
        whenever(linkRepository.startVerification(anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(Result.failure(Exception()))

        val accountManager = accountManager()
        accountManager.setAccountNullable(mockConsumerSession)
        accountManager.startVerification()

        verify(linkEventsReporter).on2FAStartFailure()
    }

    @Test
    fun `confirmVerification retries on auth error`() = runSuspendTest {
        val accountManager = accountManager()
        accountManager.setAccountNullable(mockConsumerSession)

        whenever(
            linkRepository.confirmVerification(
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull()
            )
        ).thenReturn(
            Result.failure(AuthenticationException(StripeError())),
            Result.success(mockConsumerSession)
        )

        accountManager.confirmVerification("123")

        verify(linkRepository, times(2))
            .confirmVerification(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
        verify(linkRepository).lookupConsumer(anyOrNull(), anyOrNull())

        assertThat(accountManager.linkAccount.value).isNotNull()
    }

    @Test
    fun `confirmVerification does not retry on auth error if no cookie exists`() = runSuspendTest {
        whenever(cookieStore.getAuthSessionCookie()).thenReturn(null)
        val accountManager = accountManager()
        accountManager.setAccountNullable(mockConsumerSession)

        whenever(
            linkRepository.confirmVerification(
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull()
            )
        ).thenReturn(
            Result.failure(AuthenticationException(StripeError())),
            Result.success(mockConsumerSession)
        )

        accountManager.confirmVerification("123")

        verify(linkRepository)
            .confirmVerification(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
        verify(linkRepository, times(0)).lookupConsumer(anyOrNull(), anyOrNull())
    }

    @Test
    fun `listPaymentDetails retries on auth error`() = runSuspendTest {
        val accountManager = accountManager()
        accountManager.setAccountNullable(mockConsumerSession)

        whenever(linkRepository.listPaymentDetails(anyOrNull(), anyOrNull()))
            .thenReturn(
                Result.failure(AuthenticationException(StripeError())),
                Result.success(mock())
            )

        accountManager.listPaymentDetails()

        verify(linkRepository, times(2)).listPaymentDetails(anyOrNull(), anyOrNull())
        verify(linkRepository).lookupConsumer(anyOrNull(), anyOrNull())

        assertThat(accountManager.linkAccount.value).isNotNull()
    }

    @Test
    fun `listPaymentDetails does not retry on auth error if no cookie exists`() = runSuspendTest {
        whenever(cookieStore.getAuthSessionCookie()).thenReturn(null)
        val accountManager = accountManager()
        accountManager.setAccountNullable(mockConsumerSession)

        whenever(linkRepository.listPaymentDetails(anyOrNull(), anyOrNull()))
            .thenReturn(
                Result.failure(AuthenticationException(StripeError())),
                Result.success(mock())
            )

        accountManager.listPaymentDetails()

        verify(linkRepository).listPaymentDetails(anyOrNull(), anyOrNull())
        verify(linkRepository, times(0)).lookupConsumer(anyOrNull(), anyOrNull())
    }

    @Test
    fun `createFinancialConnectionsSession retries on auth error`() = runSuspendTest {
        val accountManager = accountManager()
        accountManager.setAccountNullable(mockConsumerSession)

        whenever(linkRepository.createFinancialConnectionsSession(anyOrNull(), anyOrNull()))
            .thenReturn(
                Result.failure(AuthenticationException(StripeError())),
                Result.success(mock())
            )

        accountManager.createFinancialConnectionsSession()

        verify(linkRepository, times(2))
            .createFinancialConnectionsSession(anyOrNull(), anyOrNull())
        verify(linkRepository).lookupConsumer(anyOrNull(), anyOrNull())

        assertThat(accountManager.linkAccount.value).isNotNull()
    }

    @Test
    fun `createFinancialConnectionsSession does not retry on auth error if no cookie exists`() =
        runSuspendTest {
            whenever(cookieStore.getAuthSessionCookie()).thenReturn(null)
            val accountManager = accountManager()
            accountManager.setAccountNullable(mockConsumerSession)

            whenever(linkRepository.createFinancialConnectionsSession(anyOrNull(), anyOrNull()))
                .thenReturn(
                    Result.failure(AuthenticationException(StripeError())),
                    Result.success(mock())
                )

            accountManager.createFinancialConnectionsSession()

            verify(linkRepository)
                .createFinancialConnectionsSession(anyOrNull(), anyOrNull())
            verify(linkRepository, times(0)).lookupConsumer(anyOrNull(), anyOrNull())
        }

    @Test
    fun `createPaymentDetails for bank account retries on auth error`() = runSuspendTest {
        val accountManager = accountManager()
        accountManager.setAccountNullable(mockConsumerSession)

        whenever(
            linkRepository.createBankAccountPaymentDetails(
                anyOrNull(),
                anyOrNull(),
                anyOrNull()
            )
        )
            .thenReturn(
                Result.failure(AuthenticationException(StripeError())),
                Result.success(mock())
            )

        accountManager.createBankAccountPaymentDetails("")

        verify(linkRepository, times(2))
            .createBankAccountPaymentDetails(anyOrNull(), anyOrNull(), anyOrNull())
        verify(linkRepository).lookupConsumer(anyOrNull(), anyOrNull())

        assertThat(accountManager.linkAccount.value).isNotNull()
    }

    @Test
    fun `createPaymentDetails for bank account does not retry on auth error if no cookie exists`() =
        runSuspendTest {
            whenever(cookieStore.getAuthSessionCookie()).thenReturn(null)
            val accountManager = accountManager()
            accountManager.setAccountNullable(mockConsumerSession)

            whenever(
                linkRepository.createBankAccountPaymentDetails(
                    anyOrNull(),
                    anyOrNull(),
                    anyOrNull()
                )
            )
                .thenReturn(
                    Result.failure(AuthenticationException(StripeError())),
                    Result.success(mock())
                )

            accountManager.createBankAccountPaymentDetails("")

            verify(linkRepository)
                .createBankAccountPaymentDetails(anyOrNull(), anyOrNull(), anyOrNull())
            verify(linkRepository, times(0)).lookupConsumer(anyOrNull(), anyOrNull())
        }

    @Test
    fun `createPaymentDetails for card retries on auth error`() = runSuspendTest {
        val accountManager = accountManager()
        accountManager.setAccountNullable(mockConsumerSession)

        whenever(
            linkRepository.createCardPaymentDetails(
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull()
            )
        ).thenReturn(
            Result.failure(AuthenticationException(StripeError())),
            Result.success(mock())
        )

        accountManager.createCardPaymentDetails(mock(), "", mock())

        verify(linkRepository, times(2))
            .createCardPaymentDetails(
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull()
            )
        verify(linkRepository).lookupConsumer(anyOrNull(), anyOrNull())

        assertThat(accountManager.linkAccount.value).isNotNull()
    }

    @Test
    fun `createPaymentDetails for card does not retry on auth error if no cookie exists`() =
        runSuspendTest {
            whenever(cookieStore.getAuthSessionCookie()).thenReturn(null)
            val accountManager = accountManager()
            accountManager.setAccountNullable(mockConsumerSession)

            whenever(
                linkRepository.createCardPaymentDetails(
                    anyOrNull(),
                    anyOrNull(),
                    anyOrNull(),
                    anyOrNull(),
                    anyOrNull()
                )
            ).thenReturn(
                Result.failure(AuthenticationException(StripeError())),
                Result.success(mock())
            )

            accountManager.createCardPaymentDetails(mock(), "", mock())

            verify(linkRepository)
                .createCardPaymentDetails(
                    anyOrNull(),
                    anyOrNull(),
                    anyOrNull(),
                    anyOrNull(),
                    anyOrNull()
                )
            verify(linkRepository, times(0)).lookupConsumer(anyOrNull(), anyOrNull())
        }

    @Test
    fun `updatePaymentDetails retries on auth error`() = runSuspendTest {
        val accountManager = accountManager()
        accountManager.setAccountNullable(mockConsumerSession)

        whenever(linkRepository.updatePaymentDetails(anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(
                Result.failure(AuthenticationException(StripeError())),
                Result.success(mock())
            )

        accountManager.updatePaymentDetails(mock())

        verify(linkRepository, times(2)).updatePaymentDetails(anyOrNull(), anyOrNull(), anyOrNull())
        verify(linkRepository).lookupConsumer(anyOrNull(), anyOrNull())

        assertThat(accountManager.linkAccount.value).isNotNull()
    }

    @Test
    fun `updatePaymentDetails does not retry on auth error if no cookie exists`() = runSuspendTest {
        whenever(cookieStore.getAuthSessionCookie()).thenReturn(null)
        val accountManager = accountManager()
        accountManager.setAccountNullable(mockConsumerSession)

        whenever(linkRepository.updatePaymentDetails(anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(
                Result.failure(AuthenticationException(StripeError())),
                Result.success(mock())
            )

        accountManager.updatePaymentDetails(mock())

        verify(linkRepository).updatePaymentDetails(anyOrNull(), anyOrNull(), anyOrNull())
        verify(linkRepository, times(0)).lookupConsumer(anyOrNull(), anyOrNull())

        assertThat(accountManager.linkAccount.value).isNotNull()
    }

    @Test
    fun `deletePaymentDetails retries on auth error`() = runSuspendTest {
        val accountManager = accountManager()
        accountManager.setAccountNullable(mockConsumerSession)

        whenever(linkRepository.deletePaymentDetails(anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(
                Result.failure(AuthenticationException(StripeError())),
                Result.success(mock())
            )

        accountManager.deletePaymentDetails("id")

        verify(linkRepository, times(2)).deletePaymentDetails(anyOrNull(), anyOrNull(), anyOrNull())
        verify(linkRepository).lookupConsumer(anyOrNull(), anyOrNull())

        assertThat(accountManager.linkAccount.value).isNotNull()
    }

    @Test
    fun `deletePaymentDetails does not retry on auth error if no cookie exists`() = runSuspendTest {
        whenever(cookieStore.getAuthSessionCookie()).thenReturn(null)
        val accountManager = accountManager()
        accountManager.setAccountNullable(mockConsumerSession)

        whenever(linkRepository.deletePaymentDetails(anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(
                Result.failure(AuthenticationException(StripeError())),
                Result.success(mock())
            )

        accountManager.deletePaymentDetails("id")

        verify(linkRepository).deletePaymentDetails(anyOrNull(), anyOrNull(), anyOrNull())
        verify(linkRepository, times(0)).lookupConsumer(anyOrNull(), anyOrNull())

        assertThat(accountManager.linkAccount.value).isNotNull()
    }

    private fun runSuspendTest(testBody: suspend TestScope.() -> Unit) = runTest {
        setupRepository()
        testBody()
    }

    private suspend fun setupRepository() {
        val consumerSessionLookup = mock<ConsumerSessionLookup>().apply {
            whenever(consumerSession).thenReturn(mockConsumerSession)
        }
        whenever(linkRepository.lookupConsumer(anyOrNull(), anyOrNull()))
            .thenReturn(Result.success(consumerSessionLookup))
        whenever(linkRepository.startVerification(anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(Result.success(mockConsumerSession))
        whenever(
            linkRepository.consumerSignUp(
                email = anyOrNull(),
                phone = anyOrNull(),
                country = anyOrNull(),
                name = anyOrNull(),
                authSessionCookie = anyOrNull(),
                consentAction = any()
            )
        ).thenReturn(Result.success(mockConsumerSession))
    }

    private suspend fun mockUnverifiedAccountLookup() {
        val mockConsumerSession = mock<ConsumerSession>().apply {
            whenever(clientSecret).thenReturn(CLIENT_SECRET)
        }
        val consumerSessionLookup = mock<ConsumerSessionLookup>().apply {
            whenever(consumerSession).thenReturn(mockConsumerSession)
        }
        whenever(linkRepository.lookupConsumer(anyOrNull(), anyOrNull()))
            .thenReturn(Result.success(consumerSessionLookup))
    }

    private suspend fun mockNonexistentAccountLookup() {
        val consumerSessionLookup = mock<ConsumerSessionLookup>().apply {
            whenever(exists).thenReturn(false)
        }
        whenever(linkRepository.lookupConsumer(anyOrNull(), anyOrNull()))
            .thenReturn(Result.success(consumerSessionLookup))
    }

    private fun accountManager(
        customerEmail: String? = null,
        stripeIntent: StripeIntent = mock()
    ) = LinkAccountManager(
        config = LinkPaymentLauncher.Configuration(
            stripeIntent = stripeIntent,
            customerEmail = customerEmail,
            customerName = null,
            customerPhone = null,
            customerBillingCountryCode = null,
            merchantName = "Merchant",
            shippingValues = null,
        ),
        linkRepository,
        cookieStore,
        linkEventsReporter
    )

    companion object {
        const val EMAIL = "email@stripe.com"
        const val CLIENT_SECRET = "client_secret"
        const val PUBLISHABLE_KEY = "publishable_key"
    }
}
