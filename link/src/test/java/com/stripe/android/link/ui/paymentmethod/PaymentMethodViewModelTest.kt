package com.stripe.android.link.ui.paymentmethod

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.asLiveData
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryOwner
import com.google.common.truth.Truth.assertThat
import com.stripe.android.core.Logger
import com.stripe.android.core.injection.Injectable
import com.stripe.android.core.injection.NonFallbackInjector
import com.stripe.android.financialconnections.launcher.FinancialConnectionsSheetLinkResult
import com.stripe.android.financialconnections.model.FinancialConnectionsAccount
import com.stripe.android.link.LinkActivityContract
import com.stripe.android.link.LinkActivityResult
import com.stripe.android.link.LinkActivityResult.Canceled.Reason
import com.stripe.android.link.LinkPaymentDetails
import com.stripe.android.link.LinkScreen
import com.stripe.android.link.R
import com.stripe.android.link.account.LinkAccountManager
import com.stripe.android.link.confirmation.ConfirmationManager
import com.stripe.android.link.confirmation.PaymentConfirmationCallback
import com.stripe.android.link.injection.SignedInViewModelSubcomponent
import com.stripe.android.link.model.LinkAccount
import com.stripe.android.link.model.Navigator
import com.stripe.android.link.model.PaymentDetailsFixtures
import com.stripe.android.link.model.StripeIntentFixtures
import com.stripe.android.link.ui.ErrorMessage
import com.stripe.android.link.ui.PrimaryButtonState
import com.stripe.android.link.ui.wallet.PaymentDetailsResult
import com.stripe.android.model.ConfirmStripeIntentParams
import com.stripe.android.model.ConsumerPaymentDetails
import com.stripe.android.model.FinancialConnectionsSession
import com.stripe.android.model.PaymentMethodCreateParams
import com.stripe.android.payments.paymentlauncher.PaymentResult
import com.stripe.android.ui.core.FieldValuesToParamsMapConverter
import com.stripe.android.ui.core.elements.IdentifierSpec
import com.stripe.android.ui.core.elements.LayoutSpec
import com.stripe.android.ui.core.forms.FormFieldEntry
import com.stripe.android.ui.core.injection.FormControllerSubcomponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argWhere
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import javax.inject.Provider

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class PaymentMethodViewModelTest {
    private val linkAccount = mock<LinkAccount>().apply {
        whenever(email).thenReturn("email@stripe.com")
        whenever(clientSecret).thenReturn(CLIENT_SECRET)
    }
    private val args = mock<LinkActivityContract.Args>()
    private lateinit var linkAccountManager: LinkAccountManager
    private val navigator = mock<Navigator>()
    private val confirmationManager = mock<ConfirmationManager>()
    private val logger = Logger.noop()
    private val cardFormFieldValues = mapOf(
        IdentifierSpec.CardNumber to FormFieldEntry("5555555555554444", true),
        IdentifierSpec.CardCvc to FormFieldEntry("123", true),
        IdentifierSpec.CardExpMonth to FormFieldEntry("12", true),
        IdentifierSpec.CardExpYear to FormFieldEntry("2050", true),
        IdentifierSpec.Country to FormFieldEntry("US", true),
        IdentifierSpec.PostalCode to FormFieldEntry("12345", true)
    )
    private val formControllerSubcomponent = mock<FormControllerSubcomponent>().apply {
        whenever(formController).thenReturn(mock())
    }
    private val formControllerSubcomponentBuilder =
        mock<FormControllerSubcomponent.Builder>().apply {
            whenever(formSpec(anyOrNull())).thenReturn(this)
            whenever(initialValues(anyOrNull())).thenReturn(this)
            whenever(viewOnlyFields(anyOrNull())).thenReturn(this)
            whenever(viewModelScope(anyOrNull())).thenReturn(this)
            whenever(merchantName(anyOrNull())).thenReturn(this)
            whenever(stripeIntent(anyOrNull())).thenReturn(this)
            whenever(shippingValues(anyOrNull())).thenReturn(this)
            whenever(build()).thenReturn(formControllerSubcomponent)
        }
    private val formControllerProvider = Provider { formControllerSubcomponentBuilder }

    @Before
    fun before() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        linkAccountManager = mock<LinkAccountManager>().apply {
            whenever(consumerPublishableKey).thenReturn("consumerPublishableKey")
        }
        whenever(args.stripeIntent).thenReturn(
            StripeIntentFixtures.PI_SUCCEEDED.copy(
                linkFundingSources = listOf("card", "bank_account")
            )
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onPaymentMethodSelected updates form`() {
        val viewModel = createViewModel()
        assertThat(viewModel.paymentMethod.value).isEqualTo(SupportedPaymentMethod.Card)
        verify(formControllerSubcomponentBuilder).formSpec(eq(LayoutSpec(SupportedPaymentMethod.Card.formSpec)))

        viewModel.onPaymentMethodSelected(SupportedPaymentMethod.BankAccount)
        assertThat(viewModel.paymentMethod.value).isEqualTo(SupportedPaymentMethod.BankAccount)
        verify(formControllerSubcomponentBuilder).formSpec(eq(LayoutSpec(SupportedPaymentMethod.BankAccount.formSpec)))
    }

    @Test
    fun `startPayment for card creates PaymentDetails`() = runTest {
        whenever(
            linkAccountManager.createCardPaymentDetails(anyOrNull(), anyOrNull(), anyOrNull())
        ).thenReturn(Result.success(createLinkPaymentDetails()))

        createViewModel().startPayment(cardFormFieldValues)

        val paramsCaptor = argumentCaptor<PaymentMethodCreateParams>()
        verify(linkAccountManager).createCardPaymentDetails(
            paramsCaptor.capture(),
            any(),
            anyOrNull()
        )

        assertThat(paramsCaptor.firstValue.toParamMap()).isEqualTo(
            mapOf(
                "type" to "card",
                "card" to mapOf(
                    "number" to "5555555555554444",
                    "exp_month" to "12",
                    "exp_year" to "2050",
                    "cvc" to "123"
                ),
                "billing_details" to mapOf(
                    "address" to mapOf(
                        "country" to "US",
                        "postal_code" to "12345"
                    )
                )
            )
        )
    }

    @Test
    fun `startPayment for card completes payment when PaymentDetails creation succeeds and completePayment is true`() =
        runTest {
            val value = createLinkPaymentDetails()
            whenever(
                linkAccountManager.createCardPaymentDetails(anyOrNull(), anyOrNull(), anyOrNull())
            ).thenReturn(Result.success(value))
            whenever(args.shippingValues).thenReturn(null)

            createViewModel().startPayment(cardFormFieldValues)

            val paramsCaptor = argumentCaptor<ConfirmStripeIntentParams>()
            verify(confirmationManager).confirmStripeIntent(paramsCaptor.capture(), any())

            assertThat(paramsCaptor.firstValue.toParamMap()).isEqualTo(
                mapOf(
                    "client_secret" to args.stripeIntent.clientSecret,
                    "use_stripe_sdk" to false,
                    "mandate_data" to mapOf(
                        "customer_acceptance" to mapOf(
                            "type" to "online",
                            "online" to mapOf(
                                "infer_from_client" to true
                            )
                        )
                    ),
                    "payment_method_data" to mapOf(
                        "type" to "link",
                        "link" to mapOf(
                            "payment_details_id" to "QAAAKJ6",
                            "credentials" to mapOf(
                                "consumer_session_client_secret" to CLIENT_SECRET
                            ),
                            "card" to mapOf(
                                "cvc" to "123"
                            )
                        )
                    )
                )
            )
        }

    @Test
    fun `when shippingValues are passed ConfirmStripeIntentParams has shipping`() = runTest {
        val value = createLinkPaymentDetails()
        whenever(
            linkAccountManager.createCardPaymentDetails(anyOrNull(), anyOrNull(), anyOrNull())
        ).thenReturn(Result.success(value))
        whenever(args.shippingValues).thenReturn(
            mapOf(
                IdentifierSpec.Name to "Test Name",
                IdentifierSpec.Country to "US"
            )
        )

        createViewModel().startPayment(mapOf())

        val paramsCaptor = argumentCaptor<ConfirmStripeIntentParams>()
        verify(confirmationManager).confirmStripeIntent(paramsCaptor.capture(), any())

        assertThat(paramsCaptor.firstValue.toParamMap()["shipping"]).isEqualTo(
            mapOf(
                "address" to mapOf(
                    "country" to "US"
                ),
                "name" to "Test Name"
            )
        )
    }

    @Test
    fun `startPayment for card dismisses Link on success`() = runTest {
        whenever(
            linkAccountManager.createCardPaymentDetails(anyOrNull(), anyOrNull(), anyOrNull())
        ).thenReturn(Result.success(createLinkPaymentDetails()))

        var callback: PaymentConfirmationCallback? = null
        whenever(
            confirmationManager.confirmStripeIntent(
                anyOrNull(),
                argWhere {
                    callback = it
                    true
                }
            )
        ).then {
            callback!!(Result.success(PaymentResult.Completed))
        }

        val viewModel = createViewModel()
        viewModel.startPayment(cardFormFieldValues)

        assertThat(viewModel.primaryButtonState.value).isEqualTo(PrimaryButtonState.Completed)

        advanceTimeBy(PrimaryButtonState.COMPLETED_DELAY_MS + 1)

        verify(navigator).dismiss(LinkActivityResult.Completed)
    }

    @Test
    fun `startPayment for card starts processing`() = runTest {
        whenever(
            linkAccountManager.createCardPaymentDetails(anyOrNull(), anyOrNull(), anyOrNull())
        ).thenReturn(Result.success(createLinkPaymentDetails()))

        val viewModel = createViewModel()

        var state: PrimaryButtonState? = null
        viewModel.primaryButtonState.asLiveData().observeForever {
            state = it
        }

        viewModel.startPayment(cardFormFieldValues)

        assertThat(state).isEqualTo(PrimaryButtonState.Processing)
    }

    @Test
    fun `startPayment for card stops processing on error`() = runTest {
        whenever(
            linkAccountManager.createCardPaymentDetails(anyOrNull(), anyOrNull(), anyOrNull())
        ).thenReturn(Result.success(createLinkPaymentDetails()))

        var callback: PaymentConfirmationCallback? = null
        whenever(
            confirmationManager.confirmStripeIntent(
                anyOrNull(),
                argWhere {
                    callback = it
                    true
                }
            )
        ).then {
            callback!!(Result.success(PaymentResult.Failed(Error())))
        }

        val viewModel = createViewModel()

        var state: PrimaryButtonState? = null
        viewModel.primaryButtonState.asLiveData().observeForever {
            state = it
        }

        viewModel.startPayment(cardFormFieldValues)

        assertThat(state).isEqualTo(PrimaryButtonState.Enabled)
    }

    @Test
    fun `when startPayment fails then an error message is shown`() = runTest {
        val errorMessage = "Error message"
        whenever(
            linkAccountManager.createCardPaymentDetails(anyOrNull(), anyOrNull(), anyOrNull())
        ).thenReturn(Result.failure(RuntimeException(errorMessage)))

        val viewModel = createViewModel()

        viewModel.startPayment(cardFormFieldValues)

        assertThat(viewModel.errorMessage.value).isEqualTo(ErrorMessage.Raw(errorMessage))
    }

    @Test
    fun `startPayment for bank account creates FinancialConnectionsSession`() = runTest {
        val clientSecret = "secret"
        whenever(linkAccountManager.createFinancialConnectionsSession()).thenReturn(
            Result.success(FinancialConnectionsSession(clientSecret, "id"))
        )
        val viewModel = createViewModel()
        viewModel.onPaymentMethodSelected(SupportedPaymentMethod.BankAccount)
        viewModel.startPayment(emptyMap())

        assertThat(viewModel.financialConnectionsSessionClientSecret.value).isEqualTo(clientSecret)
    }

    @Test
    fun `onFinancialConnectionsAccountLinked cancelled then state is reset`() = runTest {
        val viewModel = createViewModel()
        viewModel.onFinancialConnectionsAccountLinked(
            FinancialConnectionsSheetLinkResult.Canceled
        )

        assertThat(viewModel.primaryButtonState.value).isEqualTo(PrimaryButtonState.Enabled)
    }

    @Test
    fun `onFinancialConnectionsAccountLinked error then shows error message`() = runTest {
        val errorMessage = "error"

        val viewModel = createViewModel()
        viewModel.onFinancialConnectionsAccountLinked(
            FinancialConnectionsSheetLinkResult.Failed(Exception(errorMessage))
        )

        assertThat(viewModel.errorMessage.value).isEqualTo(ErrorMessage.Raw(errorMessage))
    }

    @Test
    fun `when account linked at root screen then it navigates to wallet`() = runTest {
        val sessionId = "session_id"
        val account = mock<ConsumerPaymentDetails.BankAccount>()
        whenever(linkAccountManager.createBankAccountPaymentDetails(any())).thenReturn(Result.success(account))
        whenever(navigator.isOnRootScreen()).thenReturn(true)

        val viewModel = createViewModel()
        viewModel.onFinancialConnectionsAccountLinked(
            FinancialConnectionsSheetLinkResult.Completed(sessionId)
        )

        verify(navigator).navigateTo(LinkScreen.Wallet, true)
    }

    @Test
    fun `when account linked not at root screen then result is set`() = runTest {
        val sessionId = "session_id"
        val accountId = "account_id"
        val account = mock<ConsumerPaymentDetails.BankAccount>().apply {
            whenever(id).thenReturn(accountId)
        }
        whenever(linkAccountManager.createBankAccountPaymentDetails(any())).thenReturn(Result.success(account))
        whenever(navigator.isOnRootScreen()).thenReturn(false)

        val viewModel = createViewModel()
        viewModel.onFinancialConnectionsAccountLinked(
            FinancialConnectionsSheetLinkResult.Completed(sessionId)
        )

        verify(navigator).setResult(
            eq(PaymentDetailsResult.KEY),
            argWhere { it is PaymentDetailsResult.Success && it.itemId == accountId }
        )
        verify(navigator).onBack(any())
    }

    @Test
    fun `when loading from arguments then form is prefilled`() = runTest {
        whenever(args.prefilledCardParams).thenReturn(createLinkPaymentDetails().originalParams)
        createViewModel(true)

        val initialValuesCaptor: KArgumentCaptor<Map<IdentifierSpec, String?>> = argumentCaptor()
        verify(formControllerSubcomponentBuilder).initialValues(initialValuesCaptor.capture())

        assertThat(initialValuesCaptor.firstValue).containsAtLeastEntriesIn(
            mapOf(
                IdentifierSpec.get("type") to "card",
                IdentifierSpec.CardNumber to "5555555555554444",
                IdentifierSpec.CardCvc to "123",
                IdentifierSpec.CardExpMonth to "12",
                IdentifierSpec.CardExpYear to "2050",
                IdentifierSpec.Country to "US",
                IdentifierSpec.PostalCode to "12345"
            )
        )
    }

    @Test
    fun `when screen is root then secondaryButtonLabel is correct`() = runTest {
        whenever(navigator.isOnRootScreen()).thenReturn(true)

        assertThat(createViewModel().secondaryButtonLabel).isEqualTo(R.string.wallet_pay_another_way)
    }

    @Test
    fun `when screen is not root then secondaryButtonLabel is correct`() = runTest {
        whenever(navigator.isOnRootScreen()).thenReturn(false)

        assertThat(createViewModel().secondaryButtonLabel).isEqualTo(R.string.cancel)
    }

    @Test
    fun `cancel navigates back`() = runTest {
        whenever(navigator.isOnRootScreen()).thenReturn(false)

        createViewModel().onSecondaryButtonClick()

        verify(navigator).onBack(userInitiated = true)
    }

    @Test
    fun `payAnotherWay dismisses, but doesn't log out`() = runTest {
        whenever(navigator.isOnRootScreen()).thenReturn(true)

        createViewModel().onSecondaryButtonClick()

        verify(navigator).cancel(reason = eq(Reason.PayAnotherWay))
        verify(linkAccountManager, never()).logout()
    }

    @Test
    fun `Factory gets initialized by Injector`() {
        val mockBuilder = mock<SignedInViewModelSubcomponent.Builder>()
        val mockSubComponent = mock<SignedInViewModelSubcomponent>()
        val vmToBeReturned = mock<PaymentMethodViewModel>()

        whenever(mockBuilder.linkAccount(any())).thenReturn(mockBuilder)
        whenever(mockBuilder.build()).thenReturn(mockSubComponent)
        whenever((mockSubComponent.paymentMethodViewModel)).thenReturn(vmToBeReturned)

        val mockSavedStateRegistryOwner = mock<SavedStateRegistryOwner>()
        val mockSavedStateRegistry = mock<SavedStateRegistry>()
        val mockLifeCycle = mock<Lifecycle>()

        whenever(mockSavedStateRegistryOwner.savedStateRegistry).thenReturn(mockSavedStateRegistry)
        whenever(mockSavedStateRegistryOwner.lifecycle).thenReturn(mockLifeCycle)
        whenever(mockLifeCycle.currentState).thenReturn(Lifecycle.State.CREATED)

        val injector = object : NonFallbackInjector {
            override fun inject(injectable: Injectable<*>) {
                val factory = injectable as PaymentMethodViewModel.Factory
                factory.subComponentBuilderProvider = Provider { mockBuilder }
            }
        }

        val factory = PaymentMethodViewModel.Factory(
            mock(),
            injector,
            false
        )
        val factorySpy = spy(factory)
        val createdViewModel = factorySpy.create(PaymentMethodViewModel::class.java)
        assertThat(createdViewModel).isEqualTo(vmToBeReturned)
    }

    private fun createViewModel(loadFromArgs: Boolean = false) =
        PaymentMethodViewModel(
            args,
            linkAccount,
            linkAccountManager,
            navigator,
            confirmationManager,
            logger,
            formControllerProvider
        ).apply { init(loadFromArgs) }

    private fun createLinkPaymentDetails() =
        PaymentDetailsFixtures.CONSUMER_SINGLE_PAYMENT_DETAILS.paymentDetails.first().let {
            LinkPaymentDetails.New(
                it,
                PaymentMethodCreateParams.createLink(
                    it.id,
                    CLIENT_SECRET,
                    mapOf("card" to mapOf("cvc" to "123"))
                ),
                FieldValuesToParamsMapConverter.transformToPaymentMethodCreateParams(
                    cardFormFieldValues,
                    "card",
                    false
                )
            )
        }

    private fun createFinancialConnectionsAccount(id: String = "id") = FinancialConnectionsAccount(
        created = 1,
        id = id,
        institutionName = "name",
        livemode = false,
        supportedPaymentMethodTypes = listOf()
    )

    companion object {
        const val CLIENT_SECRET = "client_secret"
    }
}
