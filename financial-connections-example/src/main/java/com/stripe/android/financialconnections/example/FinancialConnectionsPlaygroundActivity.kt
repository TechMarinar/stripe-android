package com.stripe.android.financialconnections.example

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.stripe.android.financialconnections.rememberFinancialConnectionsSheet
import com.stripe.android.financialconnections.rememberFinancialConnectionsSheetForToken
import com.stripe.android.payments.bankaccount.CollectBankAccountConfiguration
import com.stripe.android.payments.bankaccount.CollectBankAccountLauncher

class FinancialConnectionsPlaygroundActivity : AppCompatActivity() {

    private val viewModel by viewModels<FinancialConnectionsPlaygroundViewModel>()

    private val sharedPreferences by lazy {
        getSharedPreferences("FINANCIAL_CONNECTIONS_DEBUG", Context.MODE_PRIVATE)
    }

    private lateinit var collectBankAccountLauncher: CollectBankAccountLauncher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        collectBankAccountLauncher = CollectBankAccountLauncher.create(
            this, viewModel::onCollectBankAccountLauncherResult
        )
        setContent {
            FinancialConnectionsScreen()
        }
    }


    @Composable
    private fun FinancialConnectionsScreen() {
        val state: FinancialConnectionsPlaygroundState by viewModel.state.collectAsState()
        val viewEffect: FinancialConnectionsPlaygroundViewEffect? by viewModel.viewEffect.collectAsState(
            null
        )
        val financialConnectionsSheetForData = rememberFinancialConnectionsSheet(
            viewModel::onFinancialConnectionsSheetResult
        )

        val financialConnectionsSheetForToken = rememberFinancialConnectionsSheetForToken(
            viewModel::onFinancialConnectionsSheetForTokenResult
        )


        LaunchedEffect(viewEffect) {
            viewEffect?.let {
                when (it) {
                    is FinancialConnectionsPlaygroundViewEffect.OpenForData -> {
                        financialConnectionsSheetForData.present(it.configuration)
                    }

                    is FinancialConnectionsPlaygroundViewEffect.OpenForToken -> {
                        financialConnectionsSheetForToken.present(it.configuration)
                    }

                    is FinancialConnectionsPlaygroundViewEffect.OpenForPaymentIntent -> {
                        collectBankAccountLauncher.presentWithPaymentIntent(
                            publishableKey = it.publishableKey,
                            stripeAccountId = null,
                            clientSecret = it.paymentIntentSecret,
                            configuration = CollectBankAccountConfiguration.USBankAccount(
                                name = "Sample name",
                                email = "sampleEmail@test.com"
                            )
                        )
                    }
                }
            }
        }

        FinancialConnectionsContent(
            state = state,
            onButtonClick = viewModel::startFinancialConnectionsSession
        )
    }

    @Composable
    private fun FinancialConnectionsContent(
        state: FinancialConnectionsPlaygroundState,
        onButtonClick: (Mode, Flow) -> Unit
    ) {
        val (selectedMode, onModeSelected) = remember { mutableStateOf(Mode.values()[0]) }
        val (selectedFlow, onFlowSelected) = remember { mutableStateOf(Flow.values()[0]) }

        Scaffold(
            topBar = { TopAppBar(title = { Text("Connections Playground") }) },
            content = {
                Column(
                    modifier = Modifier
                        .padding(it)
                        .padding(16.dp)
                ) {
                    NativeOverrideSection()
                    TestModeSection(selectedMode, onModeSelected)
                    FlowSection(selectedFlow, onFlowSelected)
                    if (state.loading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Button(
                        onClick = {
                            onButtonClick(
                                selectedMode,
                                selectedFlow
                            )
                        },
                    ) {
                        Text("Connect Accounts!")
                    }
                    LazyColumn {
                        items(state.status) { item ->
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Top) {
                                Canvas(
                                    modifier = Modifier
                                        .padding(end = 8.dp, top = 6.dp)
                                        .size(6.dp)
                                ) {
                                    drawCircle(Color.Black)
                                }
                                Text(text = item, fontSize = 12.sp)
                            }
                        }
                    }

                }
            }
        )

    }

    @Composable
    private fun NativeOverrideSection() {
        val radioOptions = NativeOverride.values()
        val (selectedOption, onOptionSelected) = remember { mutableStateOf(radioOptions[0]) }
        LaunchedEffect(selectedOption) {
            sharedPreferences.edit {
                when (selectedOption) {
                    NativeOverride.None -> clear()
                    NativeOverride.Native -> putBoolean("override_native", true)
                    NativeOverride.Web -> putBoolean("override_native", false)
                }
            }
        }
        Text(
            text = "Native Override",
            style = MaterialTheme.typography.h6.merge(),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            radioOptions.forEach { text ->
                RadioButton(
                    selected = (text == selectedOption),
                    onClick = { onOptionSelected(text) }
                )
                Text(
                    text = text.name,
                    style = MaterialTheme.typography.body1.merge(),
                )
            }
        }
    }

    @Composable
    private fun TestModeSection(
        selectedOption: Mode,
        onOptionSelected: (Mode) -> Unit
    ) {
        Text(
            text = "Mode",
            style = MaterialTheme.typography.h6.merge(),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Mode.values().forEach { text ->
                RadioButton(
                    selected = (text == selectedOption),
                    onClick = { onOptionSelected(text) }
                )
                Text(
                    text = text.name,
                    style = MaterialTheme.typography.body1.merge(),
                )
            }
        }
    }

    @Composable
    private fun FlowSection(
        selectedOption: Flow,
        onOptionSelected: (Flow) -> Unit
    ) {
        Text(
            text = "Flow",
            style = MaterialTheme.typography.h6.merge(),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Flow.values().forEach { text ->
                RadioButton(
                    selected = (text == selectedOption),
                    onClick = { onOptionSelected(text) }
                )
                Text(
                    text = text.name,
                    style = MaterialTheme.typography.body1.merge(),
                )
            }
        }
    }

    @Preview
    @Composable
    fun ContentPreview() {
        FinancialConnectionsContent(
            state = FinancialConnectionsPlaygroundState(false, "pk", listOf("Result: Pending")),
            onButtonClick = { _, _ -> }
        )
    }
}
