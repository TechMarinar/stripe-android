package com.stripe.android.financialconnections.features

import com.google.common.truth.Truth
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

@RunWith(ParameterizedRobolectricTestRunner::class)
class MarkdownParserTest(
    private val paramOne: String,
    private val paramTwo: String
) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters
        fun data() = listOf(
            arrayOf(
                "this **is** some markdown with a [Link text Here](https://link-url-here.org)",
                "this <b>is</b> some markdown with a <a href=\"https://link-url-here.org\">Link text Here</a>"
            ),
            arrayOf(
                "**hola**",
                "<b>hola</b>"
            ),
        )
    }

    @Test
    fun toSpanned() {
        Truth.assertThat(MarkdownParser.toHtml(paramOne)).isEqualTo(paramTwo)
    }
}
