package com.stripe.android.ui.core.elements

import androidx.annotation.RestrictTo
import com.stripe.android.ui.core.R
import com.stripe.android.ui.core.address.AddressRepository
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
@Serializable
data class CardBillingSpec(
    @SerialName("api_path")
    override val apiPath: IdentifierSpec = IdentifierSpec.Generic("card_billing"),
    @SerialName("allowed_country_codes")
    val allowedCountryCodes: Set<String> = supportedBillingCountries
) : FormItemSpec() {
    fun transform(
        initialValues: Map<IdentifierSpec, String?>,
        addressRepository: AddressRepository,
        shippingValues: Map<IdentifierSpec, String?>?
    ): SectionElement {
        val sameAsShippingElement =
            shippingValues?.get(IdentifierSpec.SameAsShipping)
                ?.toBooleanStrictOrNull()
                ?.let {
                    SameAsShippingElement(
                        identifier = IdentifierSpec.SameAsShipping,
                        controller = SameAsShippingController(it)
                    )
                }
        val addressElement = CardBillingAddressElement(
            IdentifierSpec.Generic("credit_billing"),
            addressRepository = addressRepository,
            countryCodes = allowedCountryCodes,
            rawValuesMap = initialValues,
            sameAsShippingElement = sameAsShippingElement,
            shippingValuesMap = shippingValues
        )

        return createSectionElement(
            listOfNotNull(
                addressElement,
                sameAsShippingElement
            ),
            R.string.billing_details
        )
    }
}
