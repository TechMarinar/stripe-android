package com.stripe.android.identity.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.stripe.android.camera.framework.image.longerEdge
import com.stripe.android.core.exception.APIConnectionException
import com.stripe.android.core.exception.APIException
import com.stripe.android.core.injection.IOContext
import com.stripe.android.core.injection.UIContext
import com.stripe.android.core.model.StripeFilePurpose
import com.stripe.android.core.networking.AnalyticsRequestV2
import com.stripe.android.identity.IdentityVerificationSheetContract
import com.stripe.android.identity.analytics.AnalyticsState
import com.stripe.android.identity.analytics.FPSTracker
import com.stripe.android.identity.analytics.IdentityAnalyticsRequestFactory
import com.stripe.android.identity.analytics.ScreenTracker
import com.stripe.android.identity.camera.IdentityAggregator
import com.stripe.android.identity.injection.IdentityActivitySubcomponent
import com.stripe.android.identity.ml.BoundingBox
import com.stripe.android.identity.ml.FaceDetectorAnalyzer
import com.stripe.android.identity.ml.FaceDetectorOutput
import com.stripe.android.identity.ml.IDDetectorOutput
import com.stripe.android.identity.networking.IdentityModelFetcher
import com.stripe.android.identity.networking.IdentityRepository
import com.stripe.android.identity.networking.Resource
import com.stripe.android.identity.networking.Resource.Companion.DUMMY_RESOURCE
import com.stripe.android.identity.networking.SelfieUploadState
import com.stripe.android.identity.networking.SingleSideDocumentUploadState
import com.stripe.android.identity.networking.Status
import com.stripe.android.identity.networking.UploadedResult
import com.stripe.android.identity.networking.models.ClearDataParam
import com.stripe.android.identity.networking.models.CollectedDataParam
import com.stripe.android.identity.networking.models.CollectedDataParam.Companion.clearData
import com.stripe.android.identity.networking.models.CollectedDataParam.Companion.collectedRequirements
import com.stripe.android.identity.networking.models.CollectedDataParam.Companion.mergeWith
import com.stripe.android.identity.networking.models.DocumentUploadParam.UploadMethod
import com.stripe.android.identity.networking.models.Requirement
import com.stripe.android.identity.networking.models.VerificationPage
import com.stripe.android.identity.networking.models.VerificationPageData
import com.stripe.android.identity.networking.models.VerificationPageStaticContentDocumentCapturePage
import com.stripe.android.identity.networking.models.VerificationPageStaticContentSelfieCapturePage
import com.stripe.android.identity.states.FaceDetectorTransitioner
import com.stripe.android.identity.states.IdentityScanState
import com.stripe.android.identity.utils.IdentityIO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.CoroutineContext

/**
 * ViewModel hosted by IdentityActivity, shared across fragments.
 */
internal class IdentityViewModel constructor(
    internal val verificationArgs: IdentityVerificationSheetContract.Args,
    private val identityRepository: IdentityRepository,
    private val identityModelFetcher: IdentityModelFetcher,
    private val identityIO: IdentityIO,
    internal val identityAnalyticsRequestFactory: IdentityAnalyticsRequestFactory,
    internal val fpsTracker: FPSTracker,
    internal val screenTracker: ScreenTracker,
    private val savedStateHandle: SavedStateHandle,
    @UIContext internal val uiContext: CoroutineContext,
    @IOContext internal val workContext: CoroutineContext
) : ViewModel() {

    /**
     * StateFlow to track the upload status of high/low resolution image for front of document.
     */
    private val _documentFrontUploadedState =
        MutableStateFlow(
            savedStateHandle.get<SingleSideDocumentUploadState>(DOCUMENT_FRONT_UPLOAD_STATE)?.let {
                // If saved as Loading, the uploading coroutine would fail as the app is destroyed.
                // Clear the state when recovered.
                if (it.isLoading()) {
                    SingleSideDocumentUploadState()
                } else {
                    it
                }
            } ?: run {
                SingleSideDocumentUploadState()
            }
        )
    val documentFrontUploadedState: StateFlow<SingleSideDocumentUploadState> =
        _documentFrontUploadedState

    /**
     * StateFlow to track the upload status of high/low resolution image for back of document.
     */
    private val _documentBackUploadedState =
        MutableStateFlow(
            savedStateHandle.get<SingleSideDocumentUploadState>(DOCUMENT_BACK_UPLOAD_STATE)?.let {
                // If saved as Loading, the uploading coroutine would fail as the app is destroyed.
                // Clear the state when recovered.
                if (it.isLoading()) {
                    SingleSideDocumentUploadState()
                } else {
                    it
                }
            } ?: run {
                SingleSideDocumentUploadState()
            }
        )
    val documentBackUploadedState: StateFlow<SingleSideDocumentUploadState> =
        _documentBackUploadedState

    /**
     * StateFlow to track the upload status of high/low resolution images of selfies.
     */
    private val _selfieUploadedState = MutableStateFlow(
        savedStateHandle[SELFIE_UPLOAD_STATE] ?: run {
            SelfieUploadState()
        }
    )
    val selfieUploadState: StateFlow<SelfieUploadState> = _selfieUploadedState

    /**
     * StateFlow to track analytics status.
     */
    private val _analyticsState = MutableStateFlow(
        savedStateHandle[ANALYTICS_STATE] ?: run {
            AnalyticsState()
        }
    )
    val analyticsState: StateFlow<AnalyticsState> = _analyticsState

    /**
     * StateFlow to track the data collected so far.
     */
    private val _collectedData = MutableStateFlow(
        savedStateHandle[COLLECTED_DATA] ?: run {
            CollectedDataParam()
        }
    )
    val collectedData: StateFlow<CollectedDataParam> = _collectedData

    /**
     * StateFlow to track request status of postVerificationPageData
     */
    @VisibleForTesting
    internal val verificationPageData = MutableStateFlow<Resource<Int>>(
        savedStateHandle[VERIFICATION_PAGE_DATA] ?: run {
            Resource.idle()
        }
    )

    /**
     * StateFlow to track request status of postVerificationPageSubmit
     */
    @VisibleForTesting
    internal val verificationPageSubmit = MutableStateFlow<Resource<Int>>(
        savedStateHandle[VERIFICATION_PAGE_SUBMIT] ?: run {
            Resource.idle()
        }
    )

    /**
     * StateFlow to track missing requirements.
     */
    private val _missingRequirements = MutableStateFlow<List<Requirement>>(
        savedStateHandle[MISSING_REQUIREMENTS] ?: run {
            listOf()
        }
    )
    val missingRequirements: StateFlow<List<Requirement>> = _missingRequirements

    val frontCollectedInfo =
        _documentFrontUploadedState.combine(_collectedData) { upload, collected ->
            (upload to collected)
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            (SingleSideDocumentUploadState() to CollectedDataParam())
        )

    val backCollectedInfo =
        _documentBackUploadedState.combine(_collectedData) { upload, collected ->
            (upload to collected)
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            (SingleSideDocumentUploadState() to CollectedDataParam())
        )

    /**
     * Response for initial VerificationPage, used for building UI.
     */
    private val _verificationPage = MutableLiveData<Resource<VerificationPage>>()
    val verificationPage: LiveData<Resource<VerificationPage>> = _verificationPage

    /**
     * Network response for the IDDetector model.
     */
    private val _idDetectorModelFile = MutableLiveData<Resource<File>>()
    val idDetectorModelFile: LiveData<Resource<File>> = _idDetectorModelFile

    /**
     * Network response for the FaceDetector model.
     */
    private val _faceDetectorModelFile = MutableLiveData<Resource<File>>()
    val faceDetectorModelFile: LiveData<Resource<File>> = _faceDetectorModelFile

    data class PageAndModelFiles(
        val page: VerificationPage,
        val idDetectorFile: File,
        val faceDetectorFile: File?
    )

    /**
     * Wrapper for both page and model
     */
    val pageAndModelFiles = object : MediatorLiveData<Resource<PageAndModelFiles>>() {
        private var page: VerificationPage? = null
        private var idDetectorModel: File? = null
        private var faceDetectorModel: File? = null
        private var faceDetectorModelValueSet = false

        init {
            postValue(Resource.loading())
            addSource(verificationPage) {
                when (it.status) {
                    Status.SUCCESS -> {
                        page = it.data
                        maybePostSuccess()
                    }
                    Status.ERROR -> {
                        postValue(Resource.error("$verificationPage posts error"))
                    }
                    Status.LOADING -> {} // no-op
                    Status.IDLE -> {}
                }
            }
            addSource(idDetectorModelFile) {
                when (it.status) {
                    Status.SUCCESS -> {
                        idDetectorModel = it.data
                        maybePostSuccess()
                    }
                    Status.ERROR -> {
                        postValue(Resource.error("$idDetectorModelFile posts error"))
                    }
                    Status.LOADING -> {} // no-op
                    Status.IDLE -> {} // no-op
                }
            }
            addSource(faceDetectorModelFile) {
                when (it.status) {
                    Status.SUCCESS -> {
                        faceDetectorModelValueSet = true
                        faceDetectorModel = it.data
                        maybePostSuccess()
                    }
                    Status.ERROR -> {
                        postValue(Resource.error("$faceDetectorModelFile posts error"))
                    }
                    Status.LOADING -> {} // no-op
                    Status.IDLE -> {} // no-op
                }
            }
        }

        private fun maybePostSuccess() {
            page?.let { page ->
                idDetectorModel?.let { idDetectorModel ->
                    if (faceDetectorModelValueSet) {
                        postValue(
                            Resource.success(
                                PageAndModelFiles(
                                    page,
                                    idDetectorModel,
                                    faceDetectorModel
                                )
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * Reset document uploaded state to loading state.
     */
    internal fun resetDocumentUploadedState() {
        _documentFrontUploadedState.updateStateAndSave { SingleSideDocumentUploadState() }
        _documentBackUploadedState.updateStateAndSave { SingleSideDocumentUploadState() }
    }

    /**
     * Reset selfie uploaded state to loading state.
     */
    internal fun resetSelfieUploadedState() {
        _selfieUploadedState.updateStateAndSave {
            SelfieUploadState()
        }
    }

    /**
     * Upload high_res of an image Uri manually picked from local file storage or taken from camera.
     */
    internal fun uploadManualResult(
        uri: Uri,
        isFront: Boolean,
        docCapturePage: VerificationPageStaticContentDocumentCapturePage,
        uploadMethod: UploadMethod,
        scanType: IdentityScanState.ScanType
    ) {
        uploadDocumentImagesAndNotify(
            imageFile =
            identityIO.resizeUriAndCreateFileToUpload(
                uri,
                verificationArgs.verificationSessionId,
                false,
                if (isFront) FRONT else BACK,
                maxDimension = docCapturePage.highResImageMaxDimension,
                compressionQuality = docCapturePage.highResImageCompressionQuality
            ),
            filePurpose = requireNotNull(
                StripeFilePurpose.fromCode(docCapturePage.filePurpose)
            ),
            uploadMethod = uploadMethod,
            isHighRes = true,
            isFront = isFront,
            scanType = scanType,
            compressionQuality = docCapturePage.highResImageCompressionQuality
        )
    }

    /**
     * Upload high_res and low_res of the [IdentityAggregator.FinalResult] from scan.
     */
    internal fun uploadScanResult(
        result: IdentityAggregator.FinalResult,
        verificationPage: VerificationPage,
        targetScanType: IdentityScanState.ScanType?
    ) {
        when (result.result) {
            is IDDetectorOutput -> {
                val originalBitmap = result.frame.cameraPreviewImage.image
                val boundingBox = result.result.boundingBox
                val scores = result.result.allScores

                val isFront = when (targetScanType) {
                    IdentityScanState.ScanType.ID_FRONT -> true
                    IdentityScanState.ScanType.ID_BACK -> false
                    IdentityScanState.ScanType.DL_FRONT -> true
                    IdentityScanState.ScanType.DL_BACK -> false
                    // passport is always uploaded as front
                    IdentityScanState.ScanType.PASSPORT -> true
                    else -> {
                        Log.e(TAG, "incorrect targetScanType: $targetScanType")
                        throw IllegalStateException("incorrect targetScanType: $targetScanType")
                    }
                }
                // upload high res
                processDocumentScanResultAndUpload(
                    originalBitmap,
                    boundingBox,
                    verificationPage.documentCapture,
                    isHighRes = true,
                    isFront = isFront,
                    scores,
                    targetScanType
                )

                // upload low res
                processDocumentScanResultAndUpload(
                    originalBitmap,
                    boundingBox,
                    verificationPage.documentCapture,
                    isHighRes = false,
                    isFront = isFront,
                    scores,
                    targetScanType
                )
            }
            is FaceDetectorOutput -> {
                val filteredFrames =
                    (result.identityState.transitioner as FaceDetectorTransitioner).filteredFrames
                require(filteredFrames.size == FaceDetectorTransitioner.NUM_FILTERED_FRAMES) {
                    "FaceDetectorTransitioner incorrectly collected ${filteredFrames.size} frames " +
                        "instead of ${FaceDetectorTransitioner.NUM_FILTERED_FRAMES} frames"
                }

                listOf(
                    (FaceDetectorTransitioner.Selfie.FIRST),
                    (FaceDetectorTransitioner.Selfie.BEST),
                    (FaceDetectorTransitioner.Selfie.LAST)
                ).forEach { selfie ->
                    listOf(true, false).forEach { isHighRes ->
                        processSelfieScanResultAndUpload(
                            originalBitmap = filteredFrames[selfie.index].first.cameraPreviewImage.image,
                            boundingBox = filteredFrames[selfie.index].second.boundingBox,
                            selfieCapturePage = requireNotNull(verificationPage.selfieCapture),
                            isHighRes = isHighRes,
                            selfie = selfie
                        )
                    }
                }
            }
        }
    }

    /**
     * Processes document scan result by cropping and padding the bitmap if necessary,
     * then upload the processed file.
     */
    @VisibleForTesting
    internal fun processDocumentScanResultAndUpload(
        originalBitmap: Bitmap,
        boundingBox: BoundingBox,
        docCapturePage: VerificationPageStaticContentDocumentCapturePage,
        isHighRes: Boolean,
        isFront: Boolean,
        scores: List<Float>,
        targetScanType: IdentityScanState.ScanType
    ) {
        identityIO.resizeBitmapAndCreateFileToUpload(
            bitmap =
            if (isHighRes) {
                identityIO.cropAndPadBitmap(
                    originalBitmap,
                    boundingBox,
                    originalBitmap.longerEdge() * docCapturePage.highResImageCropPadding
                )
            } else {
                originalBitmap
            },
            verificationId = verificationArgs.verificationSessionId,
            fileName =
            StringBuilder().also { nameBuilder ->
                nameBuilder.append(verificationArgs.verificationSessionId)
                nameBuilder.append("_${if (isFront) FRONT else BACK}")
                if (!isHighRes) {
                    nameBuilder.append("_full_frame")
                }
                nameBuilder.append(".jpeg")
            }.toString(),
            maxDimension =
            if (isHighRes) {
                docCapturePage.highResImageMaxDimension
            } else {
                docCapturePage.lowResImageMaxDimension
            },
            compressionQuality =
            if (isHighRes) {
                docCapturePage.highResImageCompressionQuality
            } else {
                docCapturePage.lowResImageCompressionQuality
            }
        ).let { imageFile ->
            uploadDocumentImagesAndNotify(
                imageFile = imageFile,
                filePurpose = requireNotNull(
                    StripeFilePurpose.fromCode(docCapturePage.filePurpose)
                ),
                uploadMethod = UploadMethod.AUTOCAPTURE,
                scores = scores,
                isHighRes = isHighRes,
                isFront = isFront,
                scanType = targetScanType,
                compressionQuality =
                if (isHighRes) {
                    docCapturePage.highResImageCompressionQuality
                } else {
                    docCapturePage.lowResImageCompressionQuality
                }
            )
        }
    }

    /**
     * Update the analytics state.
     */
    internal fun updateAnalyticsState(updateBlock: (AnalyticsState) -> AnalyticsState) {
        _analyticsState.updateStateAndSave(updateBlock)
    }

    /**
     * Uploads the imageFile and notifies corresponding result [LiveData].
     */
    private fun uploadDocumentImagesAndNotify(
        imageFile: File,
        filePurpose: StripeFilePurpose,
        uploadMethod: UploadMethod,
        scores: List<Float>? = null,
        isHighRes: Boolean,
        isFront: Boolean,
        scanType: IdentityScanState.ScanType,
        compressionQuality: Float
    ) {
        viewModelScope.launch {
            if (isFront) {
                _documentFrontUploadedState
            } else {
                _documentBackUploadedState
            }.updateStateAndSave { currentState ->
                currentState.updateLoading(isHighRes = isHighRes)
            }

            runCatching {
                var uploadTime = 0L
                identityRepository.uploadImage(
                    verificationId = verificationArgs.verificationSessionId,
                    ephemeralKey = verificationArgs.ephemeralKeySecret,
                    imageFile = imageFile,
                    filePurpose = filePurpose,
                    onSuccessExecutionTimeBlock = { uploadTime = it }
                ) to uploadTime
            }.fold(
                onSuccess = { fileTimePair ->
                    identityRepository.sendAnalyticsRequest(
                        identityAnalyticsRequestFactory.imageUpload(
                            value = fileTimePair.second,
                            compressionQuality = compressionQuality,
                            scanType = scanType,
                            id = fileTimePair.first.id,
                            fileName = fileTimePair.first.filename,
                            fileSize = imageFile.length() / BYTES_IN_KB
                        )
                    )

                    updateAnalyticsState { oldState ->
                        if (isFront) {
                            oldState.copy(
                                docFrontUploadType = uploadMethod
                            )
                        } else {
                            oldState.copy(
                                docBackUploadType = uploadMethod
                            )
                        }
                    }
                    if (isFront) {
                        _documentFrontUploadedState
                    } else {
                        _documentBackUploadedState
                    }.updateStateAndSave { currentState ->
                        currentState.update(
                            isHighRes = isHighRes,
                            newResult = UploadedResult(
                                fileTimePair.first,
                                scores,
                                uploadMethod
                            )
                        )
                    }
                },
                onFailure = {
                    if (isFront) {
                        _documentFrontUploadedState
                    } else {
                        _documentBackUploadedState
                    }.updateStateAndSave { currentState ->
                        currentState.updateError(
                            isHighRes = isHighRes,
                            message = "Failed to upload file : ${imageFile.name}",
                            throwable = it
                        )
                    }
                }
            )
        }
    }

    /**
     * Processes selfie scan result by cropping and padding the bitmap if necessary,
     * then upload the processed file.
     */
    private fun processSelfieScanResultAndUpload(
        originalBitmap: Bitmap,
        boundingBox: BoundingBox,
        selfieCapturePage: VerificationPageStaticContentSelfieCapturePage,
        isHighRes: Boolean,
        selfie: FaceDetectorTransitioner.Selfie
    ) {
        identityIO.resizeBitmapAndCreateFileToUpload(
            bitmap =
            if (isHighRes) {
                identityIO.cropAndPadBitmap(
                    originalBitmap,
                    boundingBox,
                    boundingBox.width * FaceDetectorAnalyzer.INPUT_WIDTH * selfieCapturePage.highResImageCropPadding
                )
            } else {
                originalBitmap
            },
            verificationId = verificationArgs.verificationSessionId,
            fileName =
            StringBuilder().also { nameBuilder ->
                nameBuilder.append(verificationArgs.verificationSessionId)
                nameBuilder.append("_face")
                if (isHighRes) {
                    if (selfie != FaceDetectorTransitioner.Selfie.BEST) {
                        nameBuilder.append("_${selfie.value}_crop_frame")
                    }
                } else {
                    if (selfie == FaceDetectorTransitioner.Selfie.BEST) {
                        nameBuilder.append("_full_frame")
                    } else {
                        nameBuilder.append("_${selfie.value}_full_frame")
                    }
                }
                nameBuilder.append(".jpeg")
            }.toString(),
            maxDimension =
            if (isHighRes) {
                selfieCapturePage.highResImageMaxDimension
            } else {
                selfieCapturePage.lowResImageMaxDimension
            },
            compressionQuality =
            if (isHighRes) {
                selfieCapturePage.highResImageCompressionQuality
            } else {
                selfieCapturePage.lowResImageCompressionQuality
            }
        ).let { imageFile ->
            uploadSelfieImagesAndNotify(
                imageFile = imageFile,
                filePurpose = requireNotNull(
                    StripeFilePurpose.fromCode(selfieCapturePage.filePurpose)
                ),
                isHighRes = isHighRes,
                selfie = selfie,
                compressionQuality = if (isHighRes) {
                    selfieCapturePage.highResImageCompressionQuality
                } else {
                    selfieCapturePage.lowResImageCompressionQuality
                }
            )
        }
    }

    private fun uploadSelfieImagesAndNotify(
        imageFile: File,
        filePurpose: StripeFilePurpose,
        isHighRes: Boolean,
        selfie: FaceDetectorTransitioner.Selfie,
        compressionQuality: Float
    ) {
        _selfieUploadedState.updateStateAndSave { currentState ->
            currentState.updateLoading(isHighRes, selfie)
        }
        viewModelScope.launch {
            runCatching {
                var uploadTime = 0L
                identityRepository.uploadImage(
                    verificationId = verificationArgs.verificationSessionId,
                    ephemeralKey = verificationArgs.ephemeralKeySecret,
                    imageFile = imageFile,
                    filePurpose = filePurpose,
                    onSuccessExecutionTimeBlock = { uploadTime = it }
                ) to uploadTime
            }.fold(
                onSuccess = { fileTimePair ->
                    identityRepository.sendAnalyticsRequest(
                        identityAnalyticsRequestFactory.imageUpload(
                            value = fileTimePair.second,
                            compressionQuality = compressionQuality,
                            scanType = IdentityScanState.ScanType.SELFIE,
                            id = fileTimePair.first.id,
                            fileName = fileTimePair.first.filename,
                            fileSize = imageFile.length() / BYTES_IN_KB
                        )
                    )
                    _selfieUploadedState.updateStateAndSave { currentState ->
                        currentState.update(
                            isHighRes = isHighRes,
                            newResult = UploadedResult(
                                fileTimePair.first
                            ),
                            selfie = selfie
                        )
                    }
                },
                onFailure = {
                    _selfieUploadedState.updateStateAndSave { currentState ->
                        currentState.updateError(
                            isHighRes = isHighRes,
                            selfie = selfie,
                            message = "Failed to upload file : ${imageFile.name}",
                            throwable = it
                        )
                    }
                }
            )
        }
    }

    /**
     * Simple wrapper for observing [verificationPage].
     */
    fun observeForVerificationPage(
        owner: LifecycleOwner,
        onSuccess: (VerificationPage) -> Unit,
        onFailure: (Throwable) -> Unit = {
            Log.d(TAG, "Failed to get VerificationPage")
        }
    ) {
        verificationPage.observe(owner) { resource ->
            when (resource.status) {
                Status.SUCCESS -> {
                    onSuccess(requireNotNull(resource.data))
                }
                Status.ERROR -> {
                    Log.e(TAG, "Fail to get VerificationPage")
                    onFailure(requireNotNull(resource.throwable))
                }
                Status.LOADING -> {} // no-op
                Status.IDLE -> {} // no-op
            }
        }
    }

    /**
     * Retrieve the VerificationPage data and post its value to [verificationPage]
     */
    fun retrieveAndBufferVerificationPage(shouldRetrieveModel: Boolean = true) {
        _verificationPage.postValue(Resource.loading())
        viewModelScope.launch {
            runCatching {
                identityRepository.retrieveVerificationPage(
                    verificationArgs.verificationSessionId,
                    verificationArgs.ephemeralKeySecret
                )
            }.fold(
                onSuccess = { verificationPage ->
                    _verificationPage.postValue(Resource.success(verificationPage))
                    identityAnalyticsRequestFactory.verificationPage = verificationPage
                    _missingRequirements.updateStateAndSave {
                        verificationPage.requirements.missing
                    }
                    if (shouldRetrieveModel) {
                        downloadModelAndPost(
                            verificationPage.documentCapture.models.idDetectorUrl,
                            _idDetectorModelFile
                        )
                        verificationPage.selfieCapture?.let { selfieCapture ->
                            downloadModelAndPost(
                                selfieCapture.models.faceDetectorUrl,
                                _faceDetectorModelFile
                            )
                        } ?: run {
                            // Selfie not required, post null
                            _faceDetectorModelFile.postValue(Resource.success(null))
                        }
                    }
                },
                onFailure = {
                    "Failed to retrieve verification page with " +
                        (
                            "sessionID: ${verificationArgs.verificationSessionId} and ephemeralKey: " +
                                verificationArgs.ephemeralKeySecret
                            ).let { msg ->
                            _verificationPage.postValue(
                                Resource.error(
                                    msg,
                                    IllegalStateException(msg, it)
                                )
                            )
                        }
                }
            )
        }
    }

    /**
     * Download an ML model and post its value to [target].
     */
    private fun downloadModelAndPost(modelUrl: String, target: MutableLiveData<Resource<File>>) {
        viewModelScope.launch {
            runCatching {
                target.postValue(Resource.loading())
                identityModelFetcher.fetchIdentityModel(modelUrl)
            }.fold(
                onSuccess = {
                    target.postValue(Resource.success(it))
                },
                onFailure = {
                    target.postValue(
                        Resource.error(
                            "Failed to download model from $modelUrl",
                            it
                        )
                    )
                }
            )
        }
    }

    /**
     * Post collected [CollectedDataParam] to update [VerificationPageData].
     */
    @Throws(
        APIConnectionException::class,
        APIException::class
    )
    suspend fun postVerificationPageData(
        collectedDataParam: CollectedDataParam
    ): VerificationPageData {
        verificationPageData.updateStateAndSave {
            Resource.loading()
        }
        identityRepository.postVerificationPageData(
            verificationArgs.verificationSessionId,
            verificationArgs.ephemeralKeySecret,
            collectedDataParam,
            calculateClearDataParam(collectedDataParam)
        ).let { verificationPageData ->
            this.verificationPageData.updateStateAndSave {
                Resource.success(DUMMY_RESOURCE)
            }
            _collectedData.updateStateAndSave { oldValue ->
                oldValue.mergeWith(collectedDataParam)
            }
            _missingRequirements.updateStateAndSave {
                requireNotNull(verificationPageData.requirements.missings) {
                    "VerificationPageDataRequirements.missings is null"
                }
            }
            return verificationPageData
        }
    }

    private fun calculateClearDataParam(dataToBeCollected: CollectedDataParam) =
        ClearDataParam.createFromRequirements(
            Requirement.values().toMutableSet().minus(
                collectedData.value.collectedRequirements()
            ).minus(dataToBeCollected.collectedRequirements())
        )

    /**
     * Submit the final [VerificationPageData].
     */
    @Throws(
        APIConnectionException::class,
        APIException::class
    )
    suspend fun postVerificationPageSubmit(): VerificationPageData {
        verificationPageSubmit.updateStateAndSave {
            Resource.loading()
        }
        identityRepository.postVerificationPageSubmit(
            verificationArgs.verificationSessionId,
            verificationArgs.ephemeralKeySecret
        ).let {
            verificationPageSubmit.updateStateAndSave {
                Resource.success(DUMMY_RESOURCE)
            }
            return it
        }
    }

    fun sendAnalyticsRequest(request: AnalyticsRequestV2) {
        viewModelScope.launch {
            identityRepository.sendAnalyticsRequest(
                request
            )
        }
    }

    /**
     * Send VerificationSucceeded analytics event with isFromFallbackUrl = false
     * based on values in [analyticsState].
     */
    fun sendSucceededAnalyticsRequestForNative() {
        viewModelScope.launch {
            analyticsState.collectLatest { latestState ->
                identityRepository.sendAnalyticsRequest(
                    identityAnalyticsRequestFactory.verificationSucceeded(
                        isFromFallbackUrl = false,
                        scanType = latestState.scanType,
                        requireSelfie = latestState.requireSelfie,
                        docFrontRetryTimes = latestState.docFrontRetryTimes,
                        docBackRetryTimes = latestState.docBackRetryTimes,
                        selfieRetryTimes = latestState.selfieRetryTimes,
                        docFrontUploadType = latestState.docFrontUploadType,
                        docBackUploadType = latestState.docBackUploadType,
                        docFrontModelScore = latestState.docFrontModelScore,
                        docBackModelScore = latestState.docBackModelScore,
                        selfieModelScore = latestState.selfieModelScore
                    )
                )
            }
        }
    }

    fun clearCollectedData(field: Requirement) {
        _collectedData.updateStateAndSave {
            it.clearData(field)
        }
    }

    fun clearUploadedData() {
        listOf(_documentFrontUploadedState, _documentBackUploadedState).forEach {
            it.updateStateAndSave {
                SingleSideDocumentUploadState()
            }
        }
    }

    /**
     * Check if there is a outstanding API request being submitted.
     */
    fun isSubmitting(): Boolean {
        return documentFrontUploadedState.value.isLoading() ||
            documentBackUploadedState.value.isLoading() ||
            selfieUploadState.value.isAnyLoading() ||
            verificationPageData.value.status == Status.LOADING ||
            verificationPageSubmit.value.status == Status.LOADING
    }

    private fun <State> MutableStateFlow<State>.updateStateAndSave(function: (State) -> State) {
        this.update(function)
        savedStateHandle[
            when (this) {
                _selfieUploadedState -> SELFIE_UPLOAD_STATE
                _analyticsState -> ANALYTICS_STATE
                _documentFrontUploadedState -> DOCUMENT_FRONT_UPLOAD_STATE
                _documentBackUploadedState -> DOCUMENT_BACK_UPLOAD_STATE
                _collectedData -> COLLECTED_DATA
                _missingRequirements -> MISSING_REQUIREMENTS
                verificationPageData -> VERIFICATION_PAGE_DATA
                verificationPageSubmit -> VERIFICATION_PAGE_SUBMIT
                else -> {
                    throw IllegalStateException("Unexpected state flow: $this")
                }
            }
        ] = this.value
    }

    internal class IdentityViewModelFactory(
        private val uiContextSupplier: () -> CoroutineContext,
        private val workContextSupplier: () -> CoroutineContext,
        private val subcomponentSupplier: () -> IdentityActivitySubcomponent
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val subcomponent = subcomponentSupplier()
            val savedStateHandle = extras.createSavedStateHandle()

            return IdentityViewModel(
                subcomponent.verificationArgs,
                subcomponent.identityRepository,
                subcomponent.identityModelFetcher,
                subcomponent.identityIO,
                subcomponent.identityAnalyticsRequestFactory,
                subcomponent.fpsTracker,
                subcomponent.screenTracker,
                savedStateHandle,
                uiContextSupplier(),
                workContextSupplier()
            ) as T
        }
    }

    internal companion object {
        val TAG: String = IdentityViewModel::class.java.simpleName
        const val FRONT = "front"
        const val BACK = "back"
        const val BYTES_IN_KB = 1024
        private const val DOCUMENT_FRONT_UPLOAD_STATE = "document_front_upload_state"
        private const val DOCUMENT_BACK_UPLOAD_STATE = "document_back_upload_state"
        private const val SELFIE_UPLOAD_STATE = "selfie_upload_state"
        private const val ANALYTICS_STATE = "analytics_upload_state"
        private const val COLLECTED_DATA = "collected_data"
        private const val MISSING_REQUIREMENTS = "missing_requirements"
        private const val VERIFICATION_PAGE_DATA = "verification_page_data"
        private const val VERIFICATION_PAGE_SUBMIT = "verification_page_submit"
    }
}
