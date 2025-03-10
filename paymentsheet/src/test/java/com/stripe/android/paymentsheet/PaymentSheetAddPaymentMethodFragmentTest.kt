package com.stripe.android.paymentsheet

import android.app.Application
import android.content.Context
import android.content.res.Resources
import android.util.DisplayMetrics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.stripe.android.ApiKeyFixtures
import com.stripe.android.PaymentConfiguration
import com.stripe.android.core.injection.WeakMapInjectorRegistry
import com.stripe.android.model.CardBrand
import com.stripe.android.model.PaymentIntent
import com.stripe.android.model.PaymentIntentFixtures
import com.stripe.android.model.PaymentIntentFixtures.PI_OFF_SESSION
import com.stripe.android.model.PaymentMethod
import com.stripe.android.model.PaymentMethodCreateParams
import com.stripe.android.model.StripeIntent
import com.stripe.android.paymentsheet.PaymentSheetFixtures.COMPOSE_FRAGMENT_ARGS
import com.stripe.android.paymentsheet.PaymentSheetFixtures.CONFIG_MINIMUM
import com.stripe.android.paymentsheet.PaymentSheetFixtures.MERCHANT_DISPLAY_NAME
import com.stripe.android.paymentsheet.model.FragmentConfig
import com.stripe.android.paymentsheet.model.FragmentConfigFixtures
import com.stripe.android.paymentsheet.model.PaymentSelection
import com.stripe.android.paymentsheet.paymentdatacollection.FormFragmentArguments
import com.stripe.android.ui.core.Amount
import com.stripe.android.ui.core.address.AddressRepository
import com.stripe.android.ui.core.forms.resources.LpmRepository
import com.stripe.android.utils.TestUtils.idleLooper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
internal class PaymentSheetAddPaymentMethodFragmentTest : PaymentSheetViewModelTestInjection() {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setup() {
        PaymentConfiguration.init(
            context,
            ApiKeyFixtures.FAKE_PUBLISHABLE_KEY
        )
    }

    @After
    override fun after() {
        super.after()
    }

    @Test
    fun `getFormArguments newLPM with customer requested save and Generic`() {
        val paymentIntent = mock<PaymentIntent>().also {
            whenever(it.paymentMethodTypes).thenReturn(listOf("card", "bancontact"))
        }
        val paymentMethodCreateParams = PaymentMethodCreateParams.createWithOverride(
            "bancontact",
            true,
            mapOf(
                "type" to "bancontact",
                "billing_details" to mapOf(
                    "name" to "Jenny Rosen"
                )
            ),
            emptySet()
        )
        val actualFromArguments = BaseAddPaymentMethodFragment.getFormArguments(
            lpmRepository.fromCode("bancontact")!!,
            paymentIntent,
            CONFIG_MINIMUM,
            MERCHANT_DISPLAY_NAME,
            Amount(50, "USD"),
            PaymentSelection.New.GenericPaymentMethod(
                context.getString(R.string.stripe_paymentsheet_payment_method_bancontact),
                R.drawable.stripe_ic_paymentsheet_pm_bancontact,
                paymentMethodCreateParams,
                PaymentSelection.CustomerRequestedSave.NoRequest
            )
        )

        assertThat(actualFromArguments.initialPaymentMethodCreateParams)
            .isEqualTo(paymentMethodCreateParams)
        assertThat(actualFromArguments.showCheckbox)
            .isFalse()
        assertThat(actualFromArguments.showCheckboxControlledFields)
            .isFalse()
    }

    @Test
    fun `getFormArguments newLPM WITH customer requested save and Card`() {
        val actualFromArguments = testCardFormArguments(
            PaymentSelection.CustomerRequestedSave.RequestReuse
        )

        assertThat(actualFromArguments.showCheckboxControlledFields)
            .isTrue()
    }

    @Test
    fun `getFormArguments newLPM WITH NO customer requested save and Card`() {
        val actualFromArguments = testCardFormArguments(
            PaymentSelection.CustomerRequestedSave.NoRequest
        )

        assertThat(actualFromArguments.showCheckboxControlledFields)
            .isFalse()
    }

    private fun testCardFormArguments(customerReuse: PaymentSelection.CustomerRequestedSave): FormFragmentArguments {
        val paymentIntent = mock<PaymentIntent>().also {
            whenever(it.paymentMethodTypes).thenReturn(listOf("card", "bancontact"))
        }
        val paymentMethodCreateParams = PaymentMethodCreateParams.createWithOverride(
            "card",
            false,
            mapOf(
                "type" to "card",
                "card" to mapOf(
                    "cvc" to "123",
                    "number" to "4242424242424242",
                    "exp_date" to "1250"
                ),
                "billing_details" to mapOf(
                    "address" to mapOf(
                        "country" to "Jenny Rosen"
                    )
                )
            ),
            emptySet()
        )
        val actualFromArguments = BaseAddPaymentMethodFragment.getFormArguments(
            LpmRepository.HardcodedCard,
            paymentIntent,
            CONFIG_MINIMUM,
            MERCHANT_DISPLAY_NAME,
            Amount(50, "USD"),
            PaymentSelection.New.Card(
                paymentMethodCreateParams,
                CardBrand.Visa,
                customerReuse
            )
        )

        assertThat(actualFromArguments.initialPaymentMethodCreateParams)
            .isEqualTo(paymentMethodCreateParams)

        return actualFromArguments
    }

    private fun convertPixelsToDp(px: Int, resources: Resources): Dp {
        return (px / (resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)).dp
    }

    @Test
    fun `started fragment should report onShowNewPaymentOptionForm() event`() {
        createFragment { _, _ ->
            idleLooper()
            verify(eventReporter).onShowNewPaymentOptionForm(any(), any())
        }
    }

    @Test
    fun `when payment intent is off session then form arguments are set correctly`() {
        val args = PaymentSheetFixtures.ARGS_CUSTOMER_WITH_GOOGLEPAY
        val stripeIntent = PI_OFF_SESSION
        createFragment(stripeIntent = stripeIntent, args = args) { fragment, _ ->
            idleLooper()

            assertThat(
                fragment.createFormArguments(LpmRepository.HardcodedCard, false)
            ).isEqualTo(
                COMPOSE_FRAGMENT_ARGS.copy(
                    paymentMethodCode = LpmRepository.HardcodedCard.code,
                    amount = createAmount(PI_OFF_SESSION),
                    showCheckbox = false,
                    showCheckboxControlledFields = true,
                    billingDetails = null
                )
            )
        }
    }

    @Test
    fun `Factory gets initialized by Injector when Injector is available`() {
        createFragment(registerInjector = true) { fragment, viewModel ->
            assertThat(fragment.sheetViewModel).isEqualTo(viewModel)
        }
    }

    @Test
    fun `Factory gets initialized with fallback when no Injector is available`() =
        kotlinx.coroutines.test.runTest(UnconfinedTestDispatcher()) {
            createFragment(registerInjector = false) { fragment, viewModel ->
                assertThat(fragment.sheetViewModel).isNotEqualTo(viewModel)
            }
        }

    private fun createAmount(paymentIntent: PaymentIntent = PaymentIntentFixtures.PI_WITH_SHIPPING) =
        Amount(paymentIntent.amount!!, paymentIntent.currency!!)

    private fun createFragment(
        args: PaymentSheetContract.Args = PaymentSheetFixtures.ARGS_CUSTOMER_WITH_GOOGLEPAY.copy(
            injectorKey = "testInjectorKeyAddFragmentTest"
        ),
        fragmentConfig: FragmentConfig? = FragmentConfigFixtures.DEFAULT,
        paymentMethods: List<PaymentMethod> = emptyList(),
        stripeIntent: StripeIntent? = PaymentIntentFixtures.PI_WITH_SHIPPING,
        registerInjector: Boolean = true,
        onReady: (
            PaymentSheetAddPaymentMethodFragment,

            PaymentSheetViewModel
        ) -> Unit
    ): FragmentScenario<PaymentSheetAddPaymentMethodFragment> {
        assertThat(WeakMapInjectorRegistry.staticCacheMap.size).isEqualTo(0)
        val viewModel = createViewModel(
            stripeIntent as PaymentIntent,
            customerRepositoryPMs = paymentMethods,
            injectorKey = args.injectorKey
        )
        idleLooper()

        // somehow the saveInstanceState for the viewModel needs to be present

        return launchFragmentInContainer<PaymentSheetAddPaymentMethodFragment>(
            bundleOf(
                PaymentSheetActivity.EXTRA_FRAGMENT_CONFIG to fragmentConfig,
                PaymentSheetActivity.EXTRA_STARTER_ARGS to args
            ),
            R.style.StripePaymentSheetDefaultTheme,
            initialState = Lifecycle.State.INITIALIZED
        ).moveToState(Lifecycle.State.CREATED).onFragment {
            if (registerInjector) {
                viewModel.setStripeIntent(stripeIntent)
                idleLooper()
                registerViewModel(args.injectorKey, viewModel, lpmRepository, addressRepository)
            } else {
                it.sheetViewModel.lpmResourceRepository.getRepository().forceUpdate(
                    stripeIntent.paymentMethodTypes,
                    null
                )
                it.sheetViewModel.setStripeIntent(stripeIntent)
                idleLooper()
            }
        }.moveToState(Lifecycle.State.STARTED).onFragment { fragment ->
            onReady(
                fragment,
                viewModel
            )
        }
    }

    companion object {
        val addressRepository =
            AddressRepository(ApplicationProvider.getApplicationContext<Context>().resources)
        val lpmRepository =
            LpmRepository(LpmRepository.LpmRepositoryArguments(ApplicationProvider.getApplicationContext<Application>().resources)).apply {
                this.forceUpdate(
                    listOf(
                        PaymentMethod.Type.Card.code,
                        PaymentMethod.Type.USBankAccount.code,
                        PaymentMethod.Type.SepaDebit.code,
                        PaymentMethod.Type.Bancontact.code
                    ),
                    null
                )
            }
        val Bancontact = lpmRepository.fromCode("bancontact")!!
        val SepaDebit = lpmRepository.fromCode("sepa_debit")!!
    }
}
