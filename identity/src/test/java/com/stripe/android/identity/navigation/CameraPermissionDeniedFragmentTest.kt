package com.stripe.android.identity.navigation

import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.stripe.android.camera.AppSettingsOpenable
import com.stripe.android.identity.R
import com.stripe.android.identity.analytics.IdentityAnalyticsRequestFactory
import com.stripe.android.identity.analytics.IdentityAnalyticsRequestFactory.Companion.EVENT_SCREEN_PRESENTED
import com.stripe.android.identity.analytics.IdentityAnalyticsRequestFactory.Companion.PARAM_EVENT_META_DATA
import com.stripe.android.identity.analytics.IdentityAnalyticsRequestFactory.Companion.PARAM_SCREEN_NAME
import com.stripe.android.identity.analytics.IdentityAnalyticsRequestFactory.Companion.SCREEN_NAME_ERROR
import com.stripe.android.identity.analytics.ScreenTracker
import com.stripe.android.identity.navigation.CameraPermissionDeniedFragment.Companion.ARG_SCAN_TYPE
import com.stripe.android.identity.networking.models.CollectedDataParam
import com.stripe.android.identity.utils.ARG_SHOULD_SHOW_TAKE_PHOTO
import com.stripe.android.identity.viewModelFactoryFor
import com.stripe.android.identity.viewmodel.IdentityViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
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
class CameraPermissionDeniedFragmentTest {
    private val mockScreenTracker = mock<ScreenTracker>()
    private val mockAppSettingsOpenable = mock<AppSettingsOpenable>()
    private val testDispatcher = UnconfinedTestDispatcher()
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
    fun `when scan type is ID_FRONT title is set and clicking upload navigates to id upload fragment`() {
        verifyFragmentWithScanType(
            CollectedDataParam.Type.IDCARD,
            R.id.IDUploadFragment,
            R.string.id_card
        )
    }

    @Test
    fun `when scan type is DL_FRONT title is set and clicking upload navigates to driver license upload fragment`() {
        verifyFragmentWithScanType(
            CollectedDataParam.Type.DRIVINGLICENSE,
            R.id.driverLicenseUploadFragment,
            R.string.driver_license
        )
    }

    @Test
    fun `when scan type is PASSPORT title is set and clicking upload navigates to passport upload fragment`() {
        verifyFragmentWithScanType(
            CollectedDataParam.Type.PASSPORT,
            R.id.passportUploadFragment,
            R.string.passport
        )
    }

    @Test
    fun `when scan type is not set message2 is hidden and top button is hidden`() {
        launchCameraPermissionDeniedFragment().onFragment {
//            val binding = BaseErrorFragmentBinding.bind(it.requireView())
//            assertThat(binding.message2.visibility).isEqualTo(View.GONE)
//            assertThat(binding.topButton.visibility).isEqualTo(View.GONE)
        }
    }

    @Test
    fun `when app setting button is clicked app setting is opened and returns to DocSelectionFragment`() {
        launchCameraPermissionDeniedFragment(CollectedDataParam.Type.IDCARD).onFragment {
            val navController = TestNavHostController(
                ApplicationProvider.getApplicationContext()
            )
            navController.setGraph(
                R.navigation.identity_nav_graph
            )
            navController.setCurrentDestination(R.id.cameraPermissionDeniedFragment)
            Navigation.setViewNavController(
                it.requireView(),
                navController
            )

//            BaseErrorFragmentBinding.bind(it.requireView()).bottomButton.callOnClick()

            verify(mockAppSettingsOpenable).openAppSettings()
            assertThat(navController.currentDestination?.id).isEqualTo(R.id.docSelectionFragment)
        }
    }

    private fun verifyFragmentWithScanType(
        type: CollectedDataParam.Type,
        @IdRes
        expectedDestination: Int = 0,
        @StringRes
        expectedTitleSuffix: Int = 0
    ) {
        launchCameraPermissionDeniedFragment(type).onFragment {
            val navController = TestNavHostController(
                ApplicationProvider.getApplicationContext()
            )
            navController.setGraph(
                R.navigation.identity_nav_graph
            )
            navController.setCurrentDestination(R.id.cameraPermissionDeniedFragment)
            Navigation.setViewNavController(
                it.requireView(),
                navController
            )

//            val binding = BaseErrorFragmentBinding.bind(it.requireView())
//            binding.topButton.callOnClick()

            assertThat(navController.currentDestination?.id).isEqualTo(
                expectedDestination
            )
            assertThat(
                requireNotNull(navController.backStack.last().arguments)
                [ARG_SHOULD_SHOW_TAKE_PHOTO]
            ).isEqualTo(false)
//            assertThat(binding.message2.text).isEqualTo(
//                it.getString(
//                    R.string.upload_file_text,
//                    it.getString(expectedTitleSuffix)
//                )
//            )

            verify(mockScreenTracker).screenTransitionStart(eq(SCREEN_NAME_ERROR), any())
            verify(mockIdentityViewModel).sendAnalyticsRequest(
                argThat {
                    eventName == EVENT_SCREEN_PRESENTED &&
                        (params[PARAM_EVENT_META_DATA] as Map<*, *>)[PARAM_SCREEN_NAME] == SCREEN_NAME_ERROR
                }
            )
        }
    }

    private fun launchCameraPermissionDeniedFragment(
        type: CollectedDataParam.Type? = null
    ) = launchFragmentInContainer(
        type?.let {
            bundleOf(
                ARG_SCAN_TYPE to type
            )
        },
        themeResId = R.style.Theme_MaterialComponents
    ) {
        CameraPermissionDeniedFragment(
            mockAppSettingsOpenable,
            viewModelFactoryFor(mockIdentityViewModel)
        )
    }
}
