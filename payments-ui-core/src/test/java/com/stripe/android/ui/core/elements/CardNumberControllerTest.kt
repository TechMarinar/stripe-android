package com.stripe.android.ui.core.elements

import androidx.lifecycle.asLiveData
import com.google.common.truth.Truth.assertThat
import com.stripe.android.cards.CardAccountRangeRepository
import com.stripe.android.cards.CardNumber
import com.stripe.android.cards.StaticCardAccountRangeSource
import com.stripe.android.model.AccountRange
import com.stripe.android.model.CardBrand
import com.stripe.android.ui.core.R
import com.stripe.android.ui.core.forms.FormFieldEntry
import com.stripe.android.utils.TestUtils.idleLooper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class CardNumberControllerTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val cardNumberController = CardNumberEditableController(
        CardNumberConfig(),
        FakeCardAccountRangeRepository(),
        testDispatcher,
        initialValue = null
    )

    @After
    fun cleanup() {
        Dispatchers.resetMain()
    }

    @Test
    fun `When invalid card number verify visible error`() {
        val errorFlowValues = mutableListOf<FieldError?>()
        cardNumberController.error.asLiveData()
            .observeForever {
                errorFlowValues.add(it)
            }

        cardNumberController.onValueChange("012")
        idleLooper()

        assertThat(errorFlowValues[errorFlowValues.size - 1]?.errorMessage)
            .isEqualTo(R.string.invalid_card_number)
    }

    @Test
    fun `Verify get the form field value correctly`() {
        val formFieldValuesFlow = mutableListOf<FormFieldEntry?>()
        cardNumberController.formFieldValue.asLiveData()
            .observeForever {
                formFieldValuesFlow.add(it)
            }

        cardNumberController.onValueChange("4242")
        idleLooper()

        assertThat(formFieldValuesFlow[formFieldValuesFlow.size - 1]?.isComplete)
            .isFalse()
        assertThat(formFieldValuesFlow[formFieldValuesFlow.size - 1]?.value)
            .isEqualTo("4242")

        cardNumberController.onValueChange("4242424242424242")
        idleLooper()

        assertThat(formFieldValuesFlow[formFieldValuesFlow.size - 1]?.isComplete)
            .isTrue()
        assertThat(formFieldValuesFlow[formFieldValuesFlow.size - 1]?.value)
            .isEqualTo("4242424242424242")
    }

    @Test
    fun `Verify error is visible based on the focus`() {
        // incomplete
        val visibleErrorFlow = mutableListOf<Boolean>()
        cardNumberController.visibleError.asLiveData()
            .observeForever {
                visibleErrorFlow.add(it)
            }

        cardNumberController.onFocusChange(true)
        cardNumberController.onValueChange("4242")
        idleLooper()

        assertThat(visibleErrorFlow[visibleErrorFlow.size - 1])
            .isFalse()

        cardNumberController.onFocusChange(false)
        idleLooper()

        assertThat(visibleErrorFlow[visibleErrorFlow.size - 1])
            .isTrue()
    }

    @Test
    fun `Entering VISA BIN does not call accountRangeRepository`() {
        var repositoryCalls = 0
        val cardNumberController = CardNumberEditableController(
            CardNumberConfig(),
            object : CardAccountRangeRepository {
                private val staticCardAccountRangeSource = StaticCardAccountRangeSource()
                override suspend fun getAccountRange(
                    cardNumber: CardNumber.Unvalidated
                ): AccountRange? {
                    repositoryCalls++
                    return cardNumber.bin?.let {
                        staticCardAccountRangeSource.getAccountRange(cardNumber)
                    }
                }

                override val loading: Flow<Boolean> = flowOf(false)
            },
            testDispatcher,
            initialValue = null
        )
        cardNumberController.onValueChange("42424242424242424242")
        idleLooper()
        assertThat(repositoryCalls).isEqualTo(0)
    }

    @Test
    fun `Entering valid 19 digit UnionPay BIN returns accountRange of 19`() {
        cardNumberController.onValueChange("6216828050000000000")
        idleLooper()
        assertThat(cardNumberController.accountRangeService.accountRange!!.panLength).isEqualTo(19)
    }

    @Test
    fun `Entering valid 16 digit UnionPay BIN returns accountRange of 16`() {
        cardNumberController.onValueChange("6282000000000000")
        idleLooper()
        assertThat(cardNumberController.accountRangeService.accountRange!!.panLength).isEqualTo(16)
    }

    @Test
    fun `trailingIcon should have multi trailing icons when field is empty`() {
        val trailingIcons = mutableListOf<TextFieldIcon?>()
        cardNumberController.trailingIcon.asLiveData().observeForever {
            trailingIcons.add(it)
        }
        cardNumberController.onValueChange("")
        idleLooper()
        assertThat(trailingIcons.first() as TextFieldIcon.MultiTrailing)
            .isEqualTo(
                TextFieldIcon.MultiTrailing(
                    staticIcons = listOf(
                        TextFieldIcon.Trailing(CardBrand.Visa.icon, isTintable = false),
                        TextFieldIcon.Trailing(CardBrand.MasterCard.icon, isTintable = false),
                        TextFieldIcon.Trailing(CardBrand.AmericanExpress.icon, isTintable = false)
                    ),
                    animatedIcons = listOf(
                        TextFieldIcon.Trailing(CardBrand.Discover.icon, isTintable = false),
                        TextFieldIcon.Trailing(CardBrand.JCB.icon, isTintable = false),
                        TextFieldIcon.Trailing(CardBrand.DinersClub.icon, isTintable = false),
                        TextFieldIcon.Trailing(CardBrand.UnionPay.icon, isTintable = false)
                    )
                )
            )
    }

    @Test
    fun `trailingIcon should have trailing icon when field matches bin`() {
        val trailingIcons = mutableListOf<TextFieldIcon?>()
        cardNumberController.trailingIcon.asLiveData().observeForever {
            trailingIcons.add(it)
        }
        cardNumberController.onValueChange("4")
        idleLooper()
        cardNumberController.onValueChange("")
        idleLooper()
        assertThat(trailingIcons[0])
            .isInstanceOf(TextFieldIcon.MultiTrailing::class.java)
        assertThat(trailingIcons[1] as TextFieldIcon.MultiTrailing)
            .isEqualTo(
                TextFieldIcon.MultiTrailing(
                    staticIcons = listOf(TextFieldIcon.Trailing(CardBrand.Visa.icon, isTintable = false)),
                    animatedIcons = listOf()
                )
            )
        assertThat(trailingIcons[2])
            .isInstanceOf(TextFieldIcon.MultiTrailing::class.java)
    }

    private class FakeCardAccountRangeRepository : CardAccountRangeRepository {
        private val staticCardAccountRangeSource = StaticCardAccountRangeSource()
        override suspend fun getAccountRange(
            cardNumber: CardNumber.Unvalidated
        ): AccountRange? {
            return cardNumber.bin?.let {
                staticCardAccountRangeSource.getAccountRange(cardNumber)
            }
        }

        override val loading: Flow<Boolean> = flowOf(false)
    }
}
