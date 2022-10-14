package com.stripe.android.ui.core.address

import androidx.annotation.StringRes
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.stripe.android.ui.core.R
import com.stripe.android.ui.core.elements.AdministrativeAreaConfig
import com.stripe.android.ui.core.elements.AdministrativeAreaElement
import com.stripe.android.ui.core.elements.DropdownFieldController
import com.stripe.android.ui.core.elements.IdentifierSpec
import com.stripe.android.ui.core.elements.PostalCodeConfig
import com.stripe.android.ui.core.elements.RowController
import com.stripe.android.ui.core.elements.RowElement
import com.stripe.android.ui.core.elements.SectionFieldElement
import com.stripe.android.ui.core.elements.SectionSingleFieldElement
import com.stripe.android.ui.core.elements.SimpleTextElement
import com.stripe.android.ui.core.elements.SimpleTextFieldConfig
import com.stripe.android.ui.core.elements.SimpleTextFieldController
import com.stripe.android.ui.core.elements.TextFieldConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.util.UUID

@Serializable
internal enum class FieldType(
    val serializedValue: String,
    val identifierSpec: IdentifierSpec,
    @StringRes val defaultLabel: Int
) {
    @SerialName("addressLine1")
    AddressLine1(
        "addressLine1",
        IdentifierSpec.Line1,
        R.string.address_label_address_line1
    ),

    @SerialName("addressLine2")
    AddressLine2(
        "addressLine2",
        IdentifierSpec.Line2,
        R.string.address_label_address_line2
    ),

    @SerialName("locality")
    Locality(
        "locality",
        IdentifierSpec.City,
        R.string.address_label_city
    ),

    @SerialName("dependentLocality")
    DependentLocality(
        "dependentLocality",
        IdentifierSpec.DependentLocality,
        R.string.address_label_city
    ),

    @SerialName("postalCode")
    PostalCode(
        "postalCode",
        IdentifierSpec.PostalCode,
        R.string.address_label_postal_code
    ) {
        override fun capitalization() = KeyboardCapitalization.None
    },

    @SerialName("sortingCode")
    SortingCode(
        "sortingCode",
        IdentifierSpec.SortingCode,
        R.string.address_label_postal_code
    ) {
        override fun capitalization() = KeyboardCapitalization.None
    },

    @SerialName("administrativeArea")
    AdministrativeArea(
        "administrativeArea",
        IdentifierSpec.State,
        NameType.State.stringResId
    ),

    @SerialName("name")
    Name(
        "name",
        IdentifierSpec.Name,
        R.string.address_label_full_name
    );

    open fun capitalization() = KeyboardCapitalization.Words

    companion object {
        fun from(value: String) = values().firstOrNull {
            it.serializedValue == value
        }
    }
}

@Serializable
internal enum class NameType(@StringRes val stringResId: Int) {
    @SerialName("area")
    Area(R.string.address_label_hk_area),

    @SerialName("cedex")
    Cedex(R.string.address_label_cedex),

    @SerialName("city")
    City(R.string.address_label_city),

    @SerialName("country")
    Country(R.string.address_label_country_or_region),

    @SerialName("county")
    County(R.string.address_label_county),

    @SerialName("department")
    Department(R.string.address_label_department),

    @SerialName("district")
    District(R.string.address_label_district),

    @SerialName("do_si")
    DoSi(R.string.address_label_kr_do_si),

    @SerialName("eircode")
    Eircode(R.string.address_label_ie_eircode),

    @SerialName("emirate")
    Emirate(R.string.address_label_ae_emirate),

    @SerialName("island")
    Island(R.string.address_label_island),

    @SerialName("neighborhood")
    Neighborhood(R.string.address_label_neighborhood),

    @SerialName("oblast")
    Oblast(R.string.address_label_oblast),

    @SerialName("parish")
    Parish(R.string.address_label_bb_jm_parish),

    @SerialName("pin")
    Pin(R.string.address_label_in_pin),

    @SerialName("post_town")
    PostTown(R.string.address_label_post_town),

    @SerialName("postal")
    Postal(R.string.address_label_postal_code),

    @SerialName("prefecture")
    Perfecture(R.string.address_label_jp_prefecture),

    @SerialName("province")
    Province(R.string.address_label_province),

    @SerialName("state")
    State(R.string.address_label_state),

    @SerialName("suburb")
    Suburb(R.string.address_label_suburb),

    @SerialName("suburb_or_city")
    SuburbOrCity(R.string.address_label_au_suburb_or_city),

    @SerialName("townland")
    Townload(R.string.address_label_ie_townland),

    @SerialName("village_township")
    VillageTownship(R.string.address_label_village_township),

    @SerialName("zip")
    Zip(R.string.address_label_zip_code)
}

@Serializable
internal class StateSchema(
    @SerialName("key")
    val key: String, // abbreviation
    @SerialName("name")
    val name: String // display name
)

@Serializable
internal class FieldSchema(
    @SerialName("isNumeric")
    val isNumeric: Boolean = false,
    @SerialName("examples")
    val examples: ArrayList<String> = arrayListOf(),
    @SerialName("nameType")
    val nameType: NameType // label,
)

@Serializable
internal class CountryAddressSchema(
    @SerialName("type")
    val type: FieldType?,
    @SerialName("required")
    val required: Boolean,
    @SerialName("schema")
    val schema: FieldSchema? = null
)

private val format = Json { ignoreUnknownKeys = true }

internal fun parseAddressesSchema(inputStream: InputStream?) =
    getJsonStringFromInputStream(inputStream)?.let {
        format.decodeFromString<ArrayList<CountryAddressSchema>>(
            it
        )
    }

private fun getJsonStringFromInputStream(inputStream: InputStream?) =
    inputStream?.bufferedReader().use { it?.readText() }

internal fun List<CountryAddressSchema>.transformToElementList(
    countryCode: String
): List<SectionFieldElement> {
    val countryAddressElements = this
        .filterNot {
            it.type == FieldType.SortingCode ||
                it.type == FieldType.DependentLocality
        }
        .mapNotNull { addressField ->
            addressField.type?.toElement(
                identifierSpec = addressField.type.identifierSpec,
                label = addressField.schema?.nameType?.stringResId
                    ?: addressField.type.defaultLabel,
                capitalization = addressField.type.capitalization(),
                keyboardType = getKeyboard(addressField.schema),
                countryCode = countryCode,
                showOptionalLabel = !addressField.required
            )
        }

    // Put it in a single row
    return combineCityAndPostal(countryAddressElements)
}

private fun FieldType.toElement(
    identifierSpec: IdentifierSpec,
    label: Int,
    capitalization: KeyboardCapitalization,
    keyboardType: KeyboardType,
    countryCode: String,
    showOptionalLabel: Boolean
): SectionSingleFieldElement {
    val simpleTextElement = SimpleTextElement(
        identifierSpec,
        SimpleTextFieldController(
            textFieldConfig = toConfig(
                label = label,
                capitalization = capitalization,
                keyboardType = keyboardType,
                countryCode = countryCode
            ),
            showOptionalLabel = showOptionalLabel
        )
    )
    return when (this) {
        FieldType.AdministrativeArea -> {
            val supportsAdministrativeAreaDropdown = listOf(
                "CA",
                "US"
            ).contains(countryCode)
            if (supportsAdministrativeAreaDropdown) {
                val country = when (countryCode) {
                    "CA" -> AdministrativeAreaConfig.Country.Canada()
                    "US" -> AdministrativeAreaConfig.Country.US()
                    else -> throw IllegalArgumentException()
                }
                AdministrativeAreaElement(
                    identifierSpec,
                    DropdownFieldController(
                        AdministrativeAreaConfig(country)
                    )
                )
            } else {
                simpleTextElement
            }
        }
        else -> simpleTextElement
    }
}

private fun FieldType.toConfig(
    label: Int,
    capitalization: KeyboardCapitalization,
    keyboardType: KeyboardType,
    countryCode: String
): TextFieldConfig {
    return when (this) {
        FieldType.PostalCode -> PostalCodeConfig(
            label = label,
            capitalization = capitalization,
            keyboard = keyboardType,
            country = countryCode
        )
        else -> SimpleTextFieldConfig(
            label = label,
            capitalization = capitalization,
            keyboard = keyboardType
        )
    }
}

private fun combineCityAndPostal(countryAddressElements: List<SectionSingleFieldElement>) =
    countryAddressElements.foldIndexed(
        listOf<SectionFieldElement?>()
    ) { index, acc, sectionSingleFieldElement ->
        if (index + 1 < countryAddressElements.size && isPostalNextToCity(
                countryAddressElements[index],
                countryAddressElements[index + 1]
            )
        ) {
            val rowFields = listOf(countryAddressElements[index], countryAddressElements[index + 1])
            acc.plus(
                RowElement(
                    IdentifierSpec.Generic("row_" + UUID.randomUUID().leastSignificantBits),
                    rowFields,
                    RowController(rowFields)
                )
            )
        } else if (acc.lastOrNull() is RowElement) {
            // skip this it is in a row
            acc.plus(null)
        } else {
            acc.plus(sectionSingleFieldElement)
        }
    }.filterNotNull()

private fun isPostalNextToCity(
    element1: SectionSingleFieldElement,
    element2: SectionSingleFieldElement
) = isCityOrPostal(element1.identifier) && isCityOrPostal(element2.identifier)

private fun isCityOrPostal(identifierSpec: IdentifierSpec) =
    identifierSpec == IdentifierSpec.PostalCode ||
        identifierSpec == IdentifierSpec.City

private fun getKeyboard(fieldSchema: FieldSchema?) = if (fieldSchema?.isNumeric == true) {
    KeyboardType.NumberPassword
} else {
    KeyboardType.Text
}
