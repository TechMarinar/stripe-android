package com.stripe.android.ui.core.elements

import androidx.annotation.RestrictTo
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stripe.android.ui.core.paymentsColors
import com.stripe.android.ui.core.paymentsShapes

@Composable
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
fun SectionElementUI(
    enabled: Boolean,
    element: SectionElement,
    hiddenIdentifiers: Set<IdentifierSpec>,
    lastTextFieldIdentifier: IdentifierSpec?
) {
    if (!hiddenIdentifiers.contains(element.identifier)) {
        val controller = element.controller

        val error by controller.error.collectAsState(null)
        val sectionErrorString = error?.let {
            it.formatArgs?.let { args ->
                stringResource(
                    it.errorMessage,
                    *args
                )
            } ?: stringResource(it.errorMessage)
        }

        val elementsInsideCard = element.fields.filter {
            !it.shouldRenderOutsideCard
        }
        val elementsOutsideCard = element.fields.filter {
            it.shouldRenderOutsideCard
        }

        Section(
            controller.label,
            sectionErrorString,
            contentOutsideCard = {
                elementsOutsideCard.forEach { field ->
                    SectionFieldElementUI(
                        enabled,
                        field,
                        hiddenIdentifiers = hiddenIdentifiers,
                        lastTextFieldIdentifier = lastTextFieldIdentifier
                    )
                }
            },
            contentInCard = {
                elementsInsideCard.forEachIndexed { index, field ->
                    SectionFieldElementUI(
                        enabled,
                        field,
                        hiddenIdentifiers = hiddenIdentifiers,
                        lastTextFieldIdentifier = lastTextFieldIdentifier
                    )
                    if (index != elementsInsideCard.lastIndex) {
                        Divider(
                            color = MaterialTheme.paymentsColors.componentDivider,
                            thickness = MaterialTheme.paymentsShapes.borderStrokeWidth.dp,
                            modifier = Modifier.padding(
                                horizontal = MaterialTheme.paymentsShapes.borderStrokeWidth.dp
                            )
                        )
                    }
                }
            }
        )
    }
}
