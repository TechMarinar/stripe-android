package com.stripe.android.identity.navigation

import androidx.core.os.bundleOf
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.stripe.android.identity.IdentityVerificationSheet.VerificationFlowResult
import com.stripe.android.identity.R
import com.stripe.android.identity.VerificationFlowFinishable
import com.stripe.android.identity.analytics.IdentityAnalyticsRequestFactory
import com.stripe.android.identity.analytics.IdentityAnalyticsRequestFactory.Companion.EVENT_GENERIC_ERROR
import com.stripe.android.identity.analytics.IdentityAnalyticsRequestFactory.Companion.EVENT_SCREEN_PRESENTED
import com.stripe.android.identity.analytics.IdentityAnalyticsRequestFactory.Companion.PARAM_EVENT_META_DATA
import com.stripe.android.identity.analytics.IdentityAnalyticsRequestFactory.Companion.PARAM_MESSAGE
import com.stripe.android.identity.analytics.IdentityAnalyticsRequestFactory.Companion.PARAM_SCREEN_NAME
import com.stripe.android.identity.analytics.IdentityAnalyticsRequestFactory.Companion.PARAM_STACKTRACE
import com.stripe.android.identity.analytics.IdentityAnalyticsRequestFactory.Companion.SCREEN_NAME_ERROR
import com.stripe.android.identity.analytics.ScreenTracker
import com.stripe.android.identity.navigation.ErrorDestination.Companion.ARG_CAUSE
import com.stripe.android.identity.navigation.ErrorDestination.Companion.ARG_ERROR_CONTENT
import com.stripe.android.identity.navigation.ErrorDestination.Companion.ARG_ERROR_TITLE
import com.stripe.android.identity.navigation.ErrorDestination.Companion.ARG_GO_BACK_BUTTON_DESTINATION
import com.stripe.android.identity.navigation.ErrorDestination.Companion.ARG_GO_BACK_BUTTON_TEXT
import com.stripe.android.identity.navigation.ErrorDestination.Companion.ARG_SHOULD_FAIL
import com.stripe.android.identity.navigation.ErrorDestination.Companion.UNEXPECTED_DESTINATION
import com.stripe.android.identity.viewModelFactoryFor
import com.stripe.android.identity.viewmodel.IdentityViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Ignore(
    "Jetpack compose test doesn't work with traditional navigation component in NavHostFragment, " +
        "update this test once all fragments are removed and the activity is implemented with NavHost"
)
class ErrorFragmentTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val mockVerificationFlowFinishable = mock<VerificationFlowFinishable>()
    private val mockScreenTracker = mock<ScreenTracker>()
    private val mockIdentityViewModel = mock<IdentityViewModel> {
        on { identityAnalyticsRequestFactory } doReturn
            IdentityAnalyticsRequestFactory(
                context = ApplicationProvider.getApplicationContext(),
                args = mock()
            ).also {
                it.verificationPage = mock()
            }

        on { screenTracker } doReturn mockScreenTracker
        on { uiContext } doReturn testDispatcher
        on { workContext } doReturn testDispatcher
    }

    @Test
    fun `title and content are set correctly`() {
        launchErrorFragment().onFragment {
            runBlocking {
                verify(mockScreenTracker).screenTransitionFinish(eq(SCREEN_NAME_ERROR))
            }
            verify(mockIdentityViewModel).sendAnalyticsRequest(
                argThat {
                    eventName == EVENT_SCREEN_PRESENTED &&
                        (params[PARAM_EVENT_META_DATA] as Map<*, *>)[PARAM_SCREEN_NAME] == SCREEN_NAME_ERROR
                }
            )

//            val binding = BaseErrorFragmentBinding.bind(it.requireView())
//
//            assertThat(binding.titleText.text).isEqualTo(TEST_ERROR_TITLE)
//            assertThat(binding.message1.text).isEqualTo(TEST_ERROR_CONTENT)
        }
    }

    @Test
    fun `bottom button is set correctly when set`() {
        launchErrorFragment(UNEXPECTED_DESTINATION).onFragment {
            val navController = TestNavHostController(
                ApplicationProvider.getApplicationContext()
            )
            navController.setGraph(
                R.navigation.identity_nav_graph
            )
            navController.setCurrentDestination(R.id.errorFragment)
            Navigation.setViewNavController(
                it.requireView(),
                navController
            )
//            val binding = BaseErrorFragmentBinding.bind(it.requireView())
//
//            assertThat(binding.topButton.visibility).isEqualTo(View.GONE)
//            assertThat(binding.bottomButton.visibility).isEqualTo(View.VISIBLE)
//            assertThat(binding.bottomButton.text).isEqualTo(TEST_GO_BACK_BUTTON_TEXT)
//
//            binding.bottomButton.callOnClick()

            verify(mockScreenTracker).screenTransitionStart(eq(SCREEN_NAME_ERROR), any())

            assertThat(navController.currentDestination?.id)
                .isEqualTo(R.id.consentFragment)
        }
    }

    @Test
    fun `clicking bottom button finishes the flow when failed reason is set`() {
        val mockFailedReason = mock<Throwable>()
        launchErrorFragmentWithFailedReason(mockFailedReason).onFragment {
            val navController = TestNavHostController(
                ApplicationProvider.getApplicationContext()
            )
            navController.setGraph(
                R.navigation.identity_nav_graph
            )
            navController.setCurrentDestination(R.id.errorFragment)
            Navigation.setViewNavController(
                it.requireView(),
                navController
            )
//            val binding = BaseErrorFragmentBinding.bind(it.requireView())

//            assertThat(binding.topButton.visibility).isEqualTo(View.GONE)
//            assertThat(binding.bottomButton.visibility).isEqualTo(View.VISIBLE)
//            assertThat(binding.bottomButton.text).isEqualTo(TEST_GO_BACK_BUTTON_TEXT)
//
//            binding.bottomButton.callOnClick()

            verify(mockScreenTracker).screenTransitionStart(eq(SCREEN_NAME_ERROR), any())

            val resultCaptor = argumentCaptor<VerificationFlowResult.Failed>()
            verify(mockVerificationFlowFinishable).finishWithResult(
                resultCaptor.capture()
            )
            assertThat(resultCaptor.firstValue.throwable).isSameInstanceAs(mockFailedReason)
        }
    }

    @Test
    fun `when destination is present in backstack, clicking back keep popping until destination is reached`() {
        val navigationDestination = R.id.consentFragment

        launchErrorFragment(navigationDestination).onFragment {
            val navController = TestNavHostController(
                ApplicationProvider.getApplicationContext()
            )
            navController.setGraph(
                R.navigation.identity_nav_graph
            )
            navController.setCurrentDestination(R.id.consentFragment)
            navController.navigate(R.id.action_consentFragment_to_docSelectionFragment)
            navController.navigate(R.id.action_global_errorFragment)

            Navigation.setViewNavController(
                it.requireView(),
                navController
            )

            // back stack: [consentFragment, docSelectionFragment, errorFragment]
            assertThat(navController.currentDestination?.id).isEqualTo(R.id.errorFragment)

            // keep popping until navigationDestination(consentFragment) is reached
//            BaseErrorFragmentBinding.bind(it.requireView()).bottomButton.callOnClick()

            verify(mockScreenTracker).screenTransitionStart(eq(SCREEN_NAME_ERROR), any())

            assertThat(navController.currentDestination?.id).isEqualTo(navigationDestination)
        }
    }

    @Test
    fun `when destination is not present in backstack, clicking back reaches the first entry`() {
        val navigationDestination = R.id.confirmationFragment
        val firstEntry = R.id.consentFragment
        launchErrorFragment(navigationDestination).onFragment {
            val navController = TestNavHostController(
                ApplicationProvider.getApplicationContext()
            )
            navController.setGraph(
                R.navigation.identity_nav_graph
            )
            navController.setCurrentDestination(firstEntry)
            navController.navigate(R.id.action_consentFragment_to_docSelectionFragment)
            navController.navigate(R.id.action_global_errorFragment)

            Navigation.setViewNavController(
                it.requireView(),
                navController
            )

            // back stack: [consentFragment, docSelectionFragment, errorFragment]
            assertThat(navController.currentDestination?.id).isEqualTo(R.id.errorFragment)

            // navigationDestination(confirmationFragment) is not in backstack,
            // keep popping until firstEntry(consentFragment) is reached
//            BaseErrorFragmentBinding.bind(it.requireView()).bottomButton.callOnClick()

            verify(mockScreenTracker).screenTransitionStart(eq(SCREEN_NAME_ERROR), any())

            assertThat(navController.currentDestination?.id).isEqualTo(firstEntry)
        }
    }

    private fun launchErrorFragment(
        navigationDestination: Int? = null
    ) = launchFragmentInContainer(
        bundleOf(
            ARG_ERROR_TITLE to TEST_ERROR_TITLE,
            ARG_ERROR_CONTENT to TEST_ERROR_CONTENT,
            ARG_CAUSE to TEST_CAUSE,
            ARG_GO_BACK_BUTTON_TEXT to TEST_GO_BACK_BUTTON_TEXT
        ).also { bundle ->
            navigationDestination?.let {
                bundle.putInt(ARG_GO_BACK_BUTTON_DESTINATION, navigationDestination)
            }
        },
        themeResId = R.style.Theme_MaterialComponents
    ) {
        ErrorFragment(mock(), viewModelFactoryFor(mockIdentityViewModel))
    }.onFragment {
        verify(mockIdentityViewModel).sendAnalyticsRequest(
            argThat {
                eventName == EVENT_GENERIC_ERROR &&
                    (params[PARAM_EVENT_META_DATA] as Map<*, *>)[PARAM_MESSAGE] == TEST_CAUSE.message &&
                    (params[PARAM_EVENT_META_DATA] as Map<*, *>)[PARAM_STACKTRACE] == TEST_CAUSE.stackTraceToString()
            }
        )
    }

    private fun launchErrorFragmentWithFailedReason(
        throwable: Throwable
    ) = launchFragmentInContainer(
        bundleOf(
            ARG_ERROR_TITLE to TEST_ERROR_TITLE,
            ARG_ERROR_CONTENT to TEST_ERROR_CONTENT,
            ARG_GO_BACK_BUTTON_TEXT to TEST_GO_BACK_BUTTON_TEXT,
            ARG_SHOULD_FAIL to true,
            ARG_CAUSE to throwable
        ),
        themeResId = R.style.Theme_MaterialComponents
    ) {
        ErrorFragment(mockVerificationFlowFinishable, viewModelFactoryFor(mockIdentityViewModel))
    }

    private companion object {
        const val TEST_ERROR_TITLE = "test error title"
        const val TEST_ERROR_CONTENT = "test error content"
        const val TEST_GO_BACK_BUTTON_TEXT = "go back"
        val TEST_CAUSE = IllegalStateException("error message")
    }
}
