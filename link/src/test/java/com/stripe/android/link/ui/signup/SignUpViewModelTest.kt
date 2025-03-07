package com.stripe.android.link.ui.signup

import androidx.lifecycle.Lifecycle
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryOwner
import com.google.common.truth.Truth.assertThat
import com.stripe.android.core.Logger
import com.stripe.android.core.injection.Injectable
import com.stripe.android.core.injection.NonFallbackInjector
import com.stripe.android.core.model.CountryCode
import com.stripe.android.link.LinkActivityContract
import com.stripe.android.link.LinkPaymentLauncher
import com.stripe.android.link.LinkScreen
import com.stripe.android.link.account.LinkAccountManager
import com.stripe.android.link.analytics.LinkEventsReporter
import com.stripe.android.link.model.LinkAccount
import com.stripe.android.link.model.Navigator
import com.stripe.android.link.model.StripeIntentFixtures
import com.stripe.android.link.ui.ErrorMessage
import com.stripe.android.link.ui.signup.SignUpViewModel.Companion.LOOKUP_DEBOUNCE_MS
import com.stripe.android.model.ConsumerSession
import com.stripe.android.model.ConsumerSignUpConsentAction
import com.stripe.android.model.PaymentIntent
import com.stripe.android.model.SetupIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class SignUpViewModelTest {
    private val config = LinkPaymentLauncher.Configuration(
        stripeIntent = StripeIntentFixtures.PI_SUCCEEDED,
        merchantName = MERCHANT_NAME,
        customerName = CUSTOMER_NAME,
        customerEmail = CUSTOMER_EMAIL,
        customerPhone = CUSTOMER_PHONE,
        customerBillingCountryCode = CUSTOMER_BILLING_COUNTRY_CODE,
        shippingValues = null,
    )
    private val defaultArgs = LinkActivityContract.Args(
        configuration = config,
        prefilledCardParams = null,
        injectionParams = LinkActivityContract.Args.InjectionParams(
            injectorKey = INJECTOR_KEY,
            productUsage = setOf(PRODUCT_USAGE),
            enableLogging = true,
            publishableKey = PUBLISHABLE_KEY,
            stripeAccountId = STRIPE_ACCOUNT_ID,
        )
    )
    private val linkAccountManager = mock<LinkAccountManager>()
    private val linkEventsReporter = mock<LinkEventsReporter>()
    private val navigator = mock<Navigator>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init sends analytics event`() = runTest(UnconfinedTestDispatcher()) {
        createViewModel()
        verify(linkEventsReporter).onSignupFlowPresented()
    }

    @Test
    fun `When email is valid then lookup is triggered with delay`() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel = createViewModel(prefilledEmail = null)
            assertThat(viewModel.signUpState.value).isEqualTo(SignUpState.InputtingEmail)

            viewModel.emailController.onRawValueChange("valid@email.com")
            assertThat(viewModel.signUpState.value).isEqualTo(SignUpState.InputtingEmail)

            // Mock a delayed response so we stay in the loading state
            linkAccountManager.stub {
                onBlocking { lookupConsumer(any(), any()) }.doSuspendableAnswer {
                    delay(100)
                    Result.success(mock())
                }
            }

            // Advance past lookup debounce delay
            advanceTimeBy(LOOKUP_DEBOUNCE_MS + 1)

            assertThat(viewModel.signUpState.value).isEqualTo(SignUpState.VerifyingEmail)
        }

    @Test
    fun `When multiple valid emails entered quickly then lookup is triggered only for last one`() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel = createViewModel(prefilledEmail = null)
            viewModel.emailController.onRawValueChange("first@email.com")
            advanceTimeBy(LOOKUP_DEBOUNCE_MS / 2)

            viewModel.emailController.onRawValueChange("second@email.com")
            advanceTimeBy(LOOKUP_DEBOUNCE_MS / 2)

            viewModel.emailController.onRawValueChange("third@email.com")
            assertThat(viewModel.signUpState.value).isEqualTo(SignUpState.InputtingEmail)

            // Mock a delayed response so we stay in the loading state
            linkAccountManager.stub {
                onBlocking { lookupConsumer(any(), any()) }.doSuspendableAnswer {
                    delay(100)
                    Result.success(mock())
                }
            }

            // Advance past lookup debounce delay
            advanceTimeBy(LOOKUP_DEBOUNCE_MS + 1)

            assertThat(viewModel.signUpState.value).isEqualTo(SignUpState.VerifyingEmail)

            val emailCaptor = argumentCaptor<String>()
            verify(linkAccountManager).lookupConsumer(emailCaptor.capture(), any())

            assertThat(emailCaptor.allValues.size).isEqualTo(1)
            assertThat(emailCaptor.firstValue).isEqualTo("third@email.com")
        }

    @Test
    fun `When email is provided it should not trigger lookup and should collect phone number`() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel = createViewModel(prefilledEmail = CUSTOMER_EMAIL)
            assertThat(viewModel.signUpState.value).isEqualTo(SignUpState.InputtingPhoneOrName)

            verify(linkAccountManager, times(0)).lookupConsumer(any(), any())
        }

    @Test
    fun `When lookupConsumer succeeds for new account then analytics event is sent`() =
        runTest(UnconfinedTestDispatcher()) {
            whenever(linkAccountManager.lookupConsumer(any(), any()))
                .thenReturn(Result.success(null))

            val viewModel = createViewModel()
            viewModel.emailController.onRawValueChange("valid@email.com")
            // Advance past lookup debounce delay
            advanceTimeBy(LOOKUP_DEBOUNCE_MS + 1)

            verify(linkEventsReporter).onSignupStarted()
        }

    @Test
    fun `When lookupConsumer fails then an error message is shown`() =
        runTest(UnconfinedTestDispatcher()) {
            val errorMessage = "Error message"
            whenever(linkAccountManager.lookupConsumer(any(), any()))
                .thenReturn(Result.failure(RuntimeException(errorMessage)))

            val viewModel = createViewModel()
            viewModel.emailController.onRawValueChange("valid@email.com")
            // Advance past lookup debounce delay
            advanceTimeBy(LOOKUP_DEBOUNCE_MS + 1)

            assertThat(viewModel.errorMessage.value).isEqualTo(ErrorMessage.Raw(errorMessage))
        }

    @Test
    fun `signUp sends correct ConsumerSignUpConsentAction`() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel = createViewModel()
            viewModel.performValidSignup()

            verify(linkAccountManager).signUp(
                any(),
                any(),
                any(),
                anyOrNull(),
                eq(ConsumerSignUpConsentAction.Button)
            )
        }

    @Test
    fun `When signUp fails then an error message is shown`() =
        runTest(UnconfinedTestDispatcher()) {
            val errorMessage = "Error message"
            whenever(linkAccountManager.signUp(any(), any(), any(), anyOrNull(), any()))
                .thenReturn(Result.failure(RuntimeException(errorMessage)))

            val viewModel = createViewModel()
            viewModel.performValidSignup()

            assertThat(viewModel.errorMessage.value).isEqualTo(ErrorMessage.Raw(errorMessage))
        }

    @Test
    fun `When signed up with unverified account then it navigates to Verification screen`() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel = createViewModel()

            val linkAccount = LinkAccount(
                mockConsumerSessionWithVerificationSession(
                    ConsumerSession.VerificationSession.SessionType.Sms,
                    ConsumerSession.VerificationSession.SessionState.Started
                )
            )

            whenever(linkAccountManager.signUp(any(), any(), any(), anyOrNull(), any()))
                .thenReturn(Result.success(linkAccount))

            viewModel.performValidSignup()

            verify(navigator).navigateTo(LinkScreen.Verification)
            assertThat(viewModel.signUpState.value).isEqualTo(SignUpState.InputtingEmail)
        }

    @Test
    fun `When signed up with verified account then it navigates to Wallet screen`() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel = createViewModel()

            val linkAccount = LinkAccount(
                mockConsumerSessionWithVerificationSession(
                    ConsumerSession.VerificationSession.SessionType.Sms,
                    ConsumerSession.VerificationSession.SessionState.Verified
                )
            )

            whenever(linkAccountManager.signUp(any(), any(), any(), anyOrNull(), any()))
                .thenReturn(Result.success(linkAccount))

            viewModel.performValidSignup()

            verify(navigator).navigateTo(LinkScreen.Wallet, true)
        }

    @Test
    fun `When signup succeeds then analytics event is sent`() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel = createViewModel()

            val linkAccount = LinkAccount(
                mockConsumerSessionWithVerificationSession(
                    ConsumerSession.VerificationSession.SessionType.Sms,
                    ConsumerSession.VerificationSession.SessionState.Verified
                )
            )

            whenever(linkAccountManager.signUp(any(), any(), any(), anyOrNull(), any()))
                .thenReturn(Result.success(linkAccount))

            viewModel.performValidSignup()

            verify(linkEventsReporter).onSignupCompleted()
        }

    @Test
    fun `When signup fails then analytics event is sent`() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel = createViewModel()

            whenever(linkAccountManager.signUp(any(), any(), any(), anyOrNull(), any()))
                .thenReturn(Result.failure(Exception()))

            viewModel.performValidSignup()

            verify(linkEventsReporter).onSignupFailure()
        }

    @Test
    fun `Doesn't require name for US consumers`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = createViewModel(
            prefilledEmail = null,
            countryCode = CountryCode.US
        )
        assertThat(viewModel.isReadyToSignUp.value).isFalse()

        viewModel.emailController.onRawValueChange("me@myself.com")
        viewModel.phoneController.onRawValueChange("1234567890")
        assertThat(viewModel.isReadyToSignUp.value).isTrue()
    }

    @Test
    fun `Requires name for non-US consumers`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = createViewModel(
            prefilledEmail = null,
            countryCode = CountryCode.CA
        )
        assertThat(viewModel.isReadyToSignUp.value).isFalse()

        viewModel.emailController.onRawValueChange("me@myself.com")
        viewModel.phoneController.onRawValueChange("1234567890")
        viewModel.nameController.onRawValueChange("")
        assertThat(viewModel.isReadyToSignUp.value).isFalse()

        viewModel.nameController.onRawValueChange("Someone from Canada")
        assertThat(viewModel.isReadyToSignUp.value).isTrue()
    }

    @Test
    fun `Prefilled values are handled correctly`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = createViewModel(
            prefilledEmail = CUSTOMER_EMAIL,
            countryCode = CountryCode.US
        )
        assertThat(viewModel.isReadyToSignUp.value).isTrue()
    }

    @Test
    fun `Factory gets initialized by Injector when Injector is available`() {
        val vmToBeReturned = mock<SignUpViewModel>()

        val mockSavedStateRegistryOwner = mock<SavedStateRegistryOwner>()
        val mockSavedStateRegistry = mock<SavedStateRegistry>()
        val mockLifeCycle = mock<Lifecycle>()

        whenever(mockSavedStateRegistryOwner.savedStateRegistry).thenReturn(mockSavedStateRegistry)
        whenever(mockSavedStateRegistryOwner.lifecycle).thenReturn(mockLifeCycle)
        whenever(mockLifeCycle.currentState).thenReturn(Lifecycle.State.CREATED)

        val injector = object : NonFallbackInjector {
            override fun inject(injectable: Injectable<*>) {
                val factory = injectable as SignUpViewModel.Factory
                factory.signUpViewModel = vmToBeReturned
            }
        }

        val factory = SignUpViewModel.Factory(injector)
        val factorySpy = spy(factory)
        val createdViewModel = factorySpy.create(SignUpViewModel::class.java)
        assertThat(createdViewModel).isEqualTo(vmToBeReturned)
    }

    private fun createViewModel(
        prefilledEmail: String? = null,
        args: LinkActivityContract.Args = defaultArgs,
        countryCode: CountryCode = CountryCode.US
    ): SignUpViewModel {
        val argsWithCountryCode = args.copy(
            configuration = config.copy(
                stripeIntent = when (val intent = args.stripeIntent) {
                    is PaymentIntent -> intent.copy(countryCode = countryCode.value)
                    is SetupIntent -> intent.copy(countryCode = countryCode.value)
                },
                customerEmail = prefilledEmail,
            )
        )
        return SignUpViewModel(
            args = argsWithCountryCode,
            linkAccountManager = linkAccountManager,
            linkEventsReporter = linkEventsReporter,
            logger = Logger.noop(),
            navigator = navigator
        )
    }

    private fun SignUpViewModel.performValidSignup() {
        emailController.onRawValueChange("email@valid.co")
        phoneController.onRawValueChange("1234567890")
        onSignUpClick()
    }

    private fun mockConsumerSessionWithVerificationSession(
        type: ConsumerSession.VerificationSession.SessionType,
        state: ConsumerSession.VerificationSession.SessionState
    ): ConsumerSession {
        val verificationSession = mock<ConsumerSession.VerificationSession>()
        whenever(verificationSession.type).thenReturn(type)
        whenever(verificationSession.state).thenReturn(state)
        val verificationSessions = listOf(verificationSession)

        val consumerSession = mock<ConsumerSession>()
        whenever(consumerSession.verificationSessions).thenReturn(verificationSessions)
        whenever(consumerSession.clientSecret).thenReturn("secret")
        whenever(consumerSession.emailAddress).thenReturn("email")
        return consumerSession
    }

    private companion object {
        const val INJECTOR_KEY = "injectorKey"
        const val PRODUCT_USAGE = "productUsage"
        const val PUBLISHABLE_KEY = "publishableKey"
        const val STRIPE_ACCOUNT_ID = "stripeAccountId"

        const val MERCHANT_NAME = "merchantName"
        const val CUSTOMER_EMAIL = "customer@email.com"
        const val CUSTOMER_PHONE = "1234567890"
        const val CUSTOMER_BILLING_COUNTRY_CODE = "US"
        const val CUSTOMER_NAME = "Customer"
    }
}
