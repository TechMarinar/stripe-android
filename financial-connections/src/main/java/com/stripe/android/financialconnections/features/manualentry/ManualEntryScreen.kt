@file:Suppress("TooManyFunctions")

package com.stripe.android.financialconnections.features.manualentry

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.airbnb.mvrx.Async
import com.airbnb.mvrx.Fail
import com.airbnb.mvrx.Uninitialized
import com.airbnb.mvrx.compose.collectAsState
import com.airbnb.mvrx.compose.mavericksViewModel
import com.stripe.android.core.exception.APIException
import com.stripe.android.core.exception.StripeException
import com.stripe.android.financialconnections.R
import com.stripe.android.financialconnections.model.FinancialConnectionsSessionManifest.Pane
import com.stripe.android.financialconnections.model.LinkAccountSessionPaymentAccount
import com.stripe.android.financialconnections.presentation.parentViewModel
import com.stripe.android.financialconnections.ui.FinancialConnectionsPreview
import com.stripe.android.financialconnections.ui.components.FinancialConnectionsButton
import com.stripe.android.financialconnections.ui.components.FinancialConnectionsOutlinedTextField
import com.stripe.android.financialconnections.ui.components.FinancialConnectionsScaffold
import com.stripe.android.financialconnections.ui.components.FinancialConnectionsTopAppBar
import com.stripe.android.financialconnections.ui.components.elevation
import com.stripe.android.financialconnections.ui.components.filtered
import com.stripe.android.financialconnections.ui.theme.FinancialConnectionsTheme

@Composable
internal fun ManualEntryScreen() {
    val viewModel: ManualEntryViewModel = mavericksViewModel()
    val parentViewModel = parentViewModel()
    val state: State<ManualEntryState> = viewModel.collectAsState()

    ManualEntryContent(
        routing = state.value.routing to state.value.routingError,
        account = state.value.account to state.value.accountError,
        accountConfirm = state.value.accountConfirm to state.value.accountConfirmError,
        isValidForm = state.value.isValidForm,
        verifyWithMicrodeposits = state.value.verifyWithMicrodeposits,
        linkPaymentAccountStatus = state.value.linkPaymentAccount,
        onRoutingEntered = viewModel::onRoutingEntered,
        onAccountEntered = viewModel::onAccountEntered,
        onAccountConfirmEntered = viewModel::onAccountConfirmEntered,
        onSubmit = viewModel::onSubmit,
    ) { parentViewModel.onCloseNoConfirmationClick(Pane.MANUAL_ENTRY) }
}

@Suppress("LongMethod")
@Composable
private fun ManualEntryContent(
    routing: Pair<String?, Int?>,
    account: Pair<String?, Int?>,
    accountConfirm: Pair<String?, Int?>,
    isValidForm: Boolean,
    verifyWithMicrodeposits: Boolean,
    linkPaymentAccountStatus: Async<LinkAccountSessionPaymentAccount>,
    onRoutingEntered: (String) -> Unit,
    onAccountEntered: (String) -> Unit,
    onAccountConfirmEntered: (String) -> Unit,
    onSubmit: () -> Unit,
    onCloseClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    FinancialConnectionsScaffold(
        topBar = {
            FinancialConnectionsTopAppBar(
                onCloseClick = onCloseClick,
                elevation = scrollState.elevation
            )
        }
    ) {
        Column(
            Modifier.fillMaxSize()
        ) {
            // Scrollable content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(
                        top = 16.dp,
                        start = 24.dp,
                        end = 24.dp,
                        bottom = 24.dp
                    )
            ) {
                var currentCheck: Int? by remember { mutableStateOf(R.drawable.stripe_check_base) }
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.stripe_manualentry_title),
                    color = FinancialConnectionsTheme.colors.textPrimary,
                    style = FinancialConnectionsTheme.typography.subtitle
                )
                Spacer(modifier = Modifier.size(24.dp))
                Box {
                    Image(
                        painter = painterResource(id = R.drawable.stripe_check_base),
                        contentDescription = "Image of bank check referencing routing number"
                    )
                    currentCheck?.let {
                        Image(
                            painter = painterResource(id = it),
                            contentDescription = "Image of bank check referencing routing number"
                        )
                    }
                }
                if (linkPaymentAccountStatus is Fail) {
                    Text(
                        text = (linkPaymentAccountStatus.error as? StripeException)?.message
                            ?: stringResource(R.string.stripe_error_generic_title),
                        color = FinancialConnectionsTheme.colors.textCritical,
                        style = FinancialConnectionsTheme.typography.body
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                }
                if (verifyWithMicrodeposits) {
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = stringResource(R.string.stripe_manualentry_microdeposits_desc),
                        color = FinancialConnectionsTheme.colors.textPrimary,
                        style = FinancialConnectionsTheme.typography.body
                    )
                }
                Spacer(modifier = Modifier.size(8.dp))

                InputWithError(
                    label = R.string.stripe_manualentry_routing,
                    hint = "123456789",
                    inputWithError = routing,
                    onInputChanged = onRoutingEntered,
                    onFocusGained = { currentCheck = R.drawable.stripe_check_routing }
                )
                Spacer(modifier = Modifier.size(24.dp))
                InputWithError(
                    label = R.string.stripe_manualentry_account,
                    hint = "000123456789",
                    inputWithError = account,
                    onInputChanged = onAccountEntered,
                    onFocusGained = { currentCheck = R.drawable.stripe_check_account }
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.stripe_manualentry_account_type_disclaimer),
                    color = FinancialConnectionsTheme.colors.textSecondary,
                    style = FinancialConnectionsTheme.typography.caption
                )
                Spacer(modifier = Modifier.size(24.dp))
                InputWithError(
                    label = R.string.stripe_manualentry_accountconfirm,
                    hint = "000123456789",
                    inputWithError = accountConfirm,
                    onInputChanged = onAccountConfirmEntered,
                    onFocusGained = { currentCheck = R.drawable.stripe_check_account }
                )
                Spacer(modifier = Modifier.weight(1f))
            }
            // Footer
            ManualEntryFooter(isValidForm, onSubmit)
        }
    }
}

@Composable
private fun ManualEntryFooter(
    isValidForm: Boolean,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier.padding(24.dp)
    ) {
        FinancialConnectionsButton(
            enabled = isValidForm,
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.stripe_manualentry_cta))
        }
    }
}

@Composable
private fun InputWithError(
    inputWithError: Pair<String?, Int?>,
    label: Int,
    hint: String,
    onFocusGained: () -> Unit,
    onInputChanged: (String) -> Unit
) {
    var textValue by remember { mutableStateOf(TextFieldValue()) }
    Text(
        text = stringResource(id = label),
        color = FinancialConnectionsTheme.colors.textSecondary,
        style = FinancialConnectionsTheme.typography.body
    )
    Spacer(modifier = Modifier.size(4.dp))
    FinancialConnectionsOutlinedTextField(
        value = textValue,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
        ),
        placeholder = {
            Text(
                text = hint,
                style = FinancialConnectionsTheme.typography.body,
                color = FinancialConnectionsTheme.colors.textDisabled
            )
        },
        isError = inputWithError.second != null,
        onValueChange = { text ->
            textValue = text.filtered { it.isDigit() }
            onInputChanged(textValue.text)
        },
        modifier = Modifier.onFocusChanged { if (it.isFocused) onFocusGained() }
    )
    if (inputWithError.second != null) {
        Text(
            text = stringResource(id = inputWithError.second!!),
            color = FinancialConnectionsTheme.colors.textCritical,
            style = FinancialConnectionsTheme.typography.captionEmphasized,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@Preview
@Composable
internal fun ManualEntryScreenPreview() {
    FinancialConnectionsPreview {
        ManualEntryContent(
            routing = "" to null,
            account = "" to null,
            accountConfirm = "" to null,
            isValidForm = true,
            verifyWithMicrodeposits = true,
            linkPaymentAccountStatus = Uninitialized,
            onRoutingEntered = {},
            onAccountEntered = {},
            onAccountConfirmEntered = {},
            onSubmit = {},
        ) {}
    }
}

@Preview
@Composable
internal fun ManualEntryScreenErrorPreview() {
    FinancialConnectionsPreview {
        ManualEntryContent(
            routing = "" to null,
            account = "" to null,
            accountConfirm = "" to null,
            isValidForm = true,
            verifyWithMicrodeposits = true,
            linkPaymentAccountStatus = Fail(
                APIException(message = "Error linking accounts")
            ),
            onRoutingEntered = {},
            onAccountEntered = {},
            onAccountConfirmEntered = {},
            onSubmit = {},
        ) {}
    }
}
