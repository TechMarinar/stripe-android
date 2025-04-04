package com.stripe.android

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.stripe.android.paymentsheet.R
import com.stripe.android.test.core.AuthorizeAction
import com.stripe.android.test.core.Automatic
import com.stripe.android.test.core.Billing
import com.stripe.android.test.core.Browser
import com.stripe.android.test.core.Currency
import com.stripe.android.test.core.Customer
import com.stripe.android.test.core.DelayedPMs
import com.stripe.android.test.core.DisableAnimationsRule
import com.stripe.android.test.core.GooglePayState
import com.stripe.android.test.core.INDIVIDUAL_TEST_TIMEOUT_SECONDS
import com.stripe.android.test.core.IntentType
import com.stripe.android.test.core.LinkState
import com.stripe.android.test.core.MyScreenCaptureProcessor
import com.stripe.android.test.core.PlaygroundTestDriver
import com.stripe.android.test.core.Shipping
import com.stripe.android.test.core.TestParameters
import com.stripe.android.test.core.TestWatcher
import com.stripe.android.test.core.ui.Selectors
import com.stripe.android.ui.core.forms.resources.LpmRepository
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.concurrent.Semaphore

/**
 * This tests that the look of the google pay screen when available on the device/emulator
 */
@RunWith(AndroidJUnit4::class)
class TestGooglePay {
    @get:Rule
    var globalTimeout: Timeout = Timeout.seconds(INDIVIDUAL_TEST_TIMEOUT_SECONDS)

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @get:Rule
    val testWatcher = TestWatcher()

    @get:Rule
    val disableAnimations = DisableAnimationsRule()

    private lateinit var device: UiDevice
    private lateinit var testDriver: PlaygroundTestDriver
    private val screenshotProcessor = MyScreenCaptureProcessor()

    @Before
    fun before() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        testDriver = PlaygroundTestDriver(device, composeTestRule, screenshotProcessor)
    }

    private val testParameters = TestParameters(
        lpmRepository.fromCode("bancontact")!!,
        Customer.New,
        LinkState.Off,
        GooglePayState.On,
        Currency.EUR,
        IntentType.Pay,
        Billing.On,
        shipping = Shipping.Off,
        delayed = DelayedPMs.Off,
        automatic = Automatic.Off,
        saveCheckboxValue = false,
        saveForFutureUseCheckboxVisible = false,
        useBrowser = Browser.Chrome,
        authorizationAction = AuthorizeAction.Authorize,
        merchantCountryCode = "GB",
    )

    @Test
    fun testGooglePayWithMultipleLPMs() {
        verifyGooglePayDividerText(
            testParameters.copy(
                googlePayState = GooglePayState.On
            ),
            R.string.stripe_paymentsheet_or_pay_using
        )
    }

    @Test
    fun testGooglePayWithOnlyCards() {
        verifyGooglePayDividerText(
            testParameters.copy(
                paymentMethod = LpmRepository.HardcodedCard,
                currency = Currency.USD,
                merchantCountryCode = "US",
                intentType = IntentType.Setup, // This means only card will show
            ),
            R.string.stripe_paymentsheet_or_pay_using
        )
    }

    private fun verifyGooglePayDividerText(
        testParameters: TestParameters,
        @StringRes expectedText: Int
    ) {
        val callbackLock = Semaphore(1)
        var googlePayAvailable = false
        val selectors = Selectors(device, composeTestRule, testParameters)

        callbackLock.acquire()
        selectors.onGooglePayAvailable(
            availableCallable = {
                googlePayAvailable = true
                callbackLock.release()
            },
            unavailableCallable = {
                callbackLock.release()
            }
        )
        callbackLock.acquire()
        callbackLock.release()

        Assume.assumeTrue("Google pay is available", googlePayAvailable)
        if (googlePayAvailable) {
            testDriver.setup(testParameters)
            testDriver.launchComplete()

            selectors.getGoogleDividerText()
                .assertTextEquals(
                    selectors.getResourceString(expectedText),
                    includeEditableText = false
                )
            testDriver.teardown()
        }
    }

    companion object {
        private val lpmRepository = LpmRepository(
            LpmRepository.LpmRepositoryArguments(
                InstrumentationRegistry.getInstrumentation().targetContext.resources
            )
        ).apply {
            forceUpdate(this.supportedPaymentMethods, null)
        }
    }
}
