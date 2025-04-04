package com.stripe.android.identity.navigation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.stripe.android.camera.scanui.CameraView
import com.stripe.android.identity.R
import com.stripe.android.identity.SUCCESS_VERIFICATION_PAGE_NOT_REQUIRE_LIVE_CAPTURE
import com.stripe.android.identity.analytics.AnalyticsState
import com.stripe.android.identity.analytics.FPSTracker
import com.stripe.android.identity.analytics.IdentityAnalyticsRequestFactory
import com.stripe.android.identity.camera.IdentityAggregator
import com.stripe.android.identity.camera.IdentityScanFlow
import com.stripe.android.identity.ml.Category
import com.stripe.android.identity.ml.FaceDetectorOutput
import com.stripe.android.identity.ml.IDDetectorOutput
import com.stripe.android.identity.networking.Resource
import com.stripe.android.identity.networking.models.VerificationPage
import com.stripe.android.identity.states.IdentityScanState
import com.stripe.android.identity.utils.SingleLiveEvent
import com.stripe.android.identity.viewModelFactoryFor
import com.stripe.android.identity.viewmodel.IdentityScanViewModel
import com.stripe.android.identity.viewmodel.IdentityViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IdentityCameraScanFragmentTest {
    @get:Rule
    var rule: TestRule = InstantTaskExecutorRule()

    private val finalResultLiveData = SingleLiveEvent<IdentityAggregator.FinalResult>()
    private val interimResultsLiveData = MutableLiveData<IdentityAggregator.InterimResult>()
    private val displayStateChangedFlow =
        MutableStateFlow<Pair<IdentityScanState, IdentityScanState?>?>(null)
    private val targetScanTypeFlow = MutableStateFlow<IdentityScanState.ScanType?>(null)
    private val mockScanFlow = mock<IdentityScanFlow>()
    private val mockFPSTracker = mock<FPSTracker>()

    private val mockIdentityScanViewModel = mock<IdentityScanViewModel>().also {
        whenever(it.identityScanFlow).thenReturn(mockScanFlow)
        whenever(it.finalResult).thenReturn(finalResultLiveData)
        whenever(it.interimResults).thenReturn(interimResultsLiveData)
        whenever(it.displayStateChangedFlow).thenReturn(displayStateChangedFlow)
        whenever(it.targetScanTypeFlow).thenReturn(targetScanTypeFlow)
        whenever(it.cameraAdapterInitialized).thenReturn(mock())
    }

    private val mockPageAndModel = MediatorLiveData<Resource<IdentityViewModel.PageAndModelFiles>>()
    private val mockIdentityAnalyticsRequestFactory = mock<IdentityAnalyticsRequestFactory>()
    private val mockIdentityViewModel = mock<IdentityViewModel>().also {
        whenever(it.pageAndModelFiles).thenReturn(mockPageAndModel)
        whenever(it.identityAnalyticsRequestFactory).thenReturn(mockIdentityAnalyticsRequestFactory)
        whenever(it.fpsTracker).thenReturn(mockFPSTracker)
    }

    @Before
    fun mockTargetScanTypeFlow() {
        targetScanTypeFlow.update { IdentityScanState.ScanType.ID_FRONT }
    }

    @Test
    fun `when document front finished result is posted send analytics`() {
        launchTestFragmentWithFinalResult(FINISHED_RESULT_ID_FRONT) {
            val updateBlockCaptor: KArgumentCaptor<(AnalyticsState) -> AnalyticsState> =
                argumentCaptor()
            verify(mockIdentityViewModel).updateAnalyticsState(
                updateBlockCaptor.capture()
            )
            val newState = updateBlockCaptor.firstValue(AnalyticsState())

            assertThat(newState.docFrontModelScore).isEqualTo(DOC_FRONT_SCORE)
        }
    }

    @Test
    fun `when document back finished result is posted send analytics`() {
        launchTestFragmentWithFinalResult(FINISHED_RESULT_ID_BACK) {
            val updateBlockCaptor: KArgumentCaptor<(AnalyticsState) -> AnalyticsState> =
                argumentCaptor()
            verify(mockIdentityViewModel).updateAnalyticsState(
                updateBlockCaptor.capture()
            )
            val newState = updateBlockCaptor.firstValue(AnalyticsState())

            assertThat(newState.docBackModelScore).isEqualTo(DOC_BACK_SCORE)
        }
    }

    @Test
    fun `when selfie finished result is posted send analytics`() {
        launchTestFragmentWithFinalResult(FINISHED_RESULT_SELFIE) {
            val updateBlockCaptor: KArgumentCaptor<(AnalyticsState) -> AnalyticsState> =
                argumentCaptor()
            verify(mockIdentityViewModel).updateAnalyticsState(
                updateBlockCaptor.capture()
            )
            val newState = updateBlockCaptor.firstValue(AnalyticsState())

            assertThat(newState.selfieModelScore).isEqualTo(SELFIE_SCORE)
        }
    }

    @Test
    fun `when document front timeout result is posted send analytics`() {
        launchTestFragmentWithFinalResult(TIMEOUT_RESULT_ID_FRONT) {
            verify(mockIdentityAnalyticsRequestFactory).documentTimeout(
                scanType = eq(IdentityScanState.ScanType.ID_FRONT)
            )

            verify(mockIdentityViewModel).sendAnalyticsRequest(anyOrNull())
        }
    }

    @Test
    fun `when document back timeout result is posted send analytics`() {
        launchTestFragmentWithFinalResult(TIMEOUT_RESULT_ID_BACK) {
            verify(mockIdentityAnalyticsRequestFactory).documentTimeout(
                scanType = eq(IdentityScanState.ScanType.ID_BACK)
            )

            verify(mockIdentityViewModel).sendAnalyticsRequest(anyOrNull())
        }
    }

    @Test
    fun `when selfie timeout result is posted send analytics`() {
        launchTestFragmentWithFinalResult(TIMEOUT_RESULT_SELFIE) {
            verify(mockIdentityAnalyticsRequestFactory).selfieTimeout()

            verify(mockIdentityViewModel).sendAnalyticsRequest(anyOrNull())
        }
    }

    @Test
    fun `when interimResult is available frame is tracked`() {
        launchFragmentInContainer(
            themeResId = R.style.Theme_MaterialComponents
        ) {
            TestFragment(
                viewModelFactoryFor(mockIdentityScanViewModel),
                viewModelFactoryFor(mockIdentityViewModel)
            )
        }.onFragment {
            interimResultsLiveData.postValue(
                IdentityAggregator.InterimResult(
                    mock<IdentityScanState.Finished>()
                )
            )
            verify(mockFPSTracker).trackFrame()
        }
    }

    @Test
    fun `when startScanning update analytics`() {
        launchTestFragment().onFragment {
            it.startScanning(IdentityScanState.ScanType.ID_FRONT)

            val updateBlockCaptor: KArgumentCaptor<(AnalyticsState) -> AnalyticsState> =
                argumentCaptor()
            verify(mockIdentityViewModel).updateAnalyticsState(
                updateBlockCaptor.capture()
            )
            var analyticsState = updateBlockCaptor.lastValue(AnalyticsState())
            assertThat(analyticsState.docFrontRetryTimes).isEqualTo(1)
            assertThat(analyticsState.docBackRetryTimes).isNull()
            assertThat(analyticsState.selfieRetryTimes).isNull()

            it.startScanning(IdentityScanState.ScanType.ID_FRONT)
            verify(mockIdentityViewModel, times(2)).updateAnalyticsState(
                updateBlockCaptor.capture()
            )
            analyticsState = updateBlockCaptor.lastValue(analyticsState)
            assertThat(analyticsState.docFrontRetryTimes).isEqualTo(2)
            assertThat(analyticsState.docBackRetryTimes).isNull()
            assertThat(analyticsState.selfieRetryTimes).isNull()

            it.startScanning(IdentityScanState.ScanType.ID_BACK)
            verify(mockIdentityViewModel, times(3)).updateAnalyticsState(
                updateBlockCaptor.capture()
            )
            analyticsState = updateBlockCaptor.lastValue(analyticsState)
            assertThat(analyticsState.docFrontRetryTimes).isEqualTo(2)
            assertThat(analyticsState.docBackRetryTimes).isEqualTo(1)
            assertThat(analyticsState.selfieRetryTimes).isNull()

            it.startScanning(IdentityScanState.ScanType.SELFIE)
            verify(mockIdentityViewModel, times(4)).updateAnalyticsState(
                updateBlockCaptor.capture()
            )
            analyticsState = updateBlockCaptor.lastValue(analyticsState)
            assertThat(analyticsState.docFrontRetryTimes).isEqualTo(2)
            assertThat(analyticsState.docBackRetryTimes).isEqualTo(1)
            assertThat(analyticsState.selfieRetryTimes).isEqualTo(1)
        }
    }

    @Test
    fun `when finalResult is posted with Finished observes for verification page and scan is stopped`() {
        launchTestFragment().onFragment { testFragment ->
            finalResultLiveData.postValue(
                mock<IdentityAggregator.FinalResult>().also {
                    whenever(it.identityState).thenReturn(mock<IdentityScanState.Finished>())
                }
            )
            interimResultsLiveData.postValue(
                IdentityAggregator.InterimResult(
                    mock<IdentityScanState.Finished> {
                        on { it.isFinal } doReturn true
                    }
                )
            )

            verify(mockIdentityViewModel).observeForVerificationPage(
                same(testFragment.viewLifecycleOwner),
                any(),
                any()
            )

            verify(mockScanFlow).resetFlow()
            assertThat(testFragment.cameraAdapter?.isBoundToLifecycle()).isFalse()
        }
    }

    private fun launchTestFragment() = launchFragmentInContainer(
        themeResId = R.style.Theme_MaterialComponents
    ) {
        TestFragment(
            viewModelFactoryFor(mockIdentityScanViewModel),
            viewModelFactoryFor(mockIdentityViewModel)
        )
    }

    private fun launchTestFragmentWithFinalResult(
        finalResult: IdentityAggregator.FinalResult,
        testBlock: () -> Unit
    ) = launchTestFragment().onFragment {
        val navController = TestNavHostController(
            ApplicationProvider.getApplicationContext()
        )
        navController.setGraph(
            R.navigation.identity_nav_graph
        )
        navController.setCurrentDestination(R.id.IDScanFragment)
        Navigation.setViewNavController(
            it.requireView(),
            navController
        )

        finalResultLiveData.postValue(
            finalResult
        )

        runBlocking {
            verify(mockFPSTracker).reportAndReset(
                if (finalResult.result is FaceDetectorOutput) {
                    eq(IdentityAnalyticsRequestFactory.TYPE_SELFIE)
                } else {
                    eq(IdentityAnalyticsRequestFactory.TYPE_DOCUMENT)
                }
            )
        }

        val successCaptor: KArgumentCaptor<(VerificationPage) -> Unit> = argumentCaptor()
        verify(mockIdentityViewModel).observeForVerificationPage(
            any(),
            successCaptor.capture(),
            any()
        )
        successCaptor.firstValue(SUCCESS_VERIFICATION_PAGE_NOT_REQUIRE_LIVE_CAPTURE)

        testBlock()
    }

    internal class TestFragment(
        identityScanViewModelFactory: ViewModelProvider.Factory,
        identityViewModelFactory: ViewModelProvider.Factory
    ) : IdentityCameraScanFragment(
        identityScanViewModelFactory,
        identityViewModelFactory
    ) {
        override val fragmentId = 0

        override fun onCameraReady() {}

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            cameraView = mock<CameraView>().also {
                whenever(it.viewFinderWindowView).thenReturn(mock())
            }
            cameraAdapter = mock()
            return View(ApplicationProvider.getApplicationContext())
        }
    }

    private companion object {
        const val DOC_FRONT_SCORE = 0.12f
        const val DOC_BACK_SCORE = 0.23f
        const val SELFIE_SCORE = 0.34f
        val FINISHED_RESULT_ID_FRONT = IdentityAggregator.FinalResult(
            frame = mock(),
            result = IDDetectorOutput(
                boundingBox = mock(),
                category = Category.ID_FRONT,
                resultScore = DOC_FRONT_SCORE,
                allScores = mock()
            ),
            identityState = IdentityScanState.Finished(
                type = IdentityScanState.ScanType.ID_FRONT,
                transitioner = mock()
            )
        )

        val FINISHED_RESULT_ID_BACK = IdentityAggregator.FinalResult(
            frame = mock(),
            result = IDDetectorOutput(
                boundingBox = mock(),
                category = Category.ID_BACK,
                resultScore = DOC_BACK_SCORE,
                allScores = mock()
            ),
            identityState = IdentityScanState.Finished(
                type = IdentityScanState.ScanType.ID_BACK,
                transitioner = mock()
            )
        )

        val FINISHED_RESULT_SELFIE = IdentityAggregator.FinalResult(
            frame = mock(),
            result = FaceDetectorOutput(
                boundingBox = mock(),
                resultScore = SELFIE_SCORE
            ),
            identityState = IdentityScanState.Finished(
                type = IdentityScanState.ScanType.SELFIE,
                transitioner = mock()
            )
        )

        val TIMEOUT_RESULT_ID_FRONT = IdentityAggregator.FinalResult(
            frame = mock(),
            result = IDDetectorOutput(
                boundingBox = mock(),
                category = Category.ID_FRONT,
                resultScore = DOC_FRONT_SCORE,
                allScores = mock()
            ),
            identityState = IdentityScanState.TimeOut(
                type = IdentityScanState.ScanType.ID_FRONT,
                transitioner = mock()
            )
        )

        val TIMEOUT_RESULT_ID_BACK = IdentityAggregator.FinalResult(
            frame = mock(),
            result = IDDetectorOutput(
                boundingBox = mock(),
                category = Category.ID_BACK,
                resultScore = DOC_BACK_SCORE,
                allScores = mock()
            ),
            identityState = IdentityScanState.TimeOut(
                type = IdentityScanState.ScanType.ID_BACK,
                transitioner = mock()
            )
        )

        val TIMEOUT_RESULT_SELFIE = IdentityAggregator.FinalResult(
            frame = mock(),
            result = FaceDetectorOutput(
                boundingBox = mock(),
                resultScore = SELFIE_SCORE
            ),
            identityState = IdentityScanState.TimeOut(
                type = IdentityScanState.ScanType.SELFIE,
                transitioner = mock()
            )
        )
    }
}
