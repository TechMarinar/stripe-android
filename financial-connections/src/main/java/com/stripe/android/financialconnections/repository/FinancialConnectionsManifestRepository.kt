package com.stripe.android.financialconnections.repository

import com.stripe.android.core.Logger
import com.stripe.android.core.exception.APIConnectionException
import com.stripe.android.core.exception.APIException
import com.stripe.android.core.exception.AuthenticationException
import com.stripe.android.core.exception.InvalidRequestException
import com.stripe.android.core.networking.ApiRequest
import com.stripe.android.financialconnections.analytics.AuthSessionEvent
import com.stripe.android.financialconnections.model.FinancialConnectionsAuthorizationSession
import com.stripe.android.financialconnections.model.FinancialConnectionsInstitution
import com.stripe.android.financialconnections.model.FinancialConnectionsSessionManifest
import com.stripe.android.financialconnections.model.SynchronizeSessionResponse
import com.stripe.android.financialconnections.network.FinancialConnectionsRequestExecutor
import com.stripe.android.financialconnections.network.NetworkConstants
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Date
import java.util.Locale

/**
 * Repository to centralize reads and writes to the [FinancialConnectionsSessionManifest]
 * of the current flow.
 */
internal interface FinancialConnectionsManifestRepository {

    /**
     * Retrieves the current cached [SynchronizeSessionResponse] instance, or fetches
     * it from backend if no cached version available.
     */
    @Throws(
        AuthenticationException::class,
        InvalidRequestException::class,
        APIConnectionException::class,
        APIException::class
    )
    suspend fun getOrFetchSynchronizeFinancialConnectionsSession(
        clientSecret: String,
        applicationId: String
    ): SynchronizeSessionResponse

    /**
     * Triggers a session synchronization, and caches the updated [SynchronizeSessionResponse] object
     * received as result.
     */
    @Throws(
        AuthenticationException::class,
        InvalidRequestException::class,
        APIConnectionException::class,
        APIException::class
    )
    suspend fun synchronizeFinancialConnectionsSession(
        clientSecret: String,
        applicationId: String
    ): SynchronizeSessionResponse

    /**
     * Marks the consent pane as completed, and caches the updated [FinancialConnectionsSessionManifest] object
     * received as result.
     */
    @Throws(
        AuthenticationException::class,
        InvalidRequestException::class,
        APIConnectionException::class,
        APIException::class
    )
    suspend fun markConsentAcquired(
        clientSecret: String
    ): FinancialConnectionsSessionManifest

    @Throws(
        AuthenticationException::class,
        InvalidRequestException::class,
        APIConnectionException::class,
        APIException::class
    )
    suspend fun postAuthorizationSession(
        clientSecret: String,
        applicationId: String,
        institution: FinancialConnectionsInstitution,
    ): FinancialConnectionsAuthorizationSession

    suspend fun postAuthorizationSessionEvent(
        clientSecret: String,
        clientTimestamp: Date,
        sessionId: String,
        authSessionEvents: List<AuthSessionEvent>
    ): FinancialConnectionsAuthorizationSession

    @Throws(
        AuthenticationException::class,
        InvalidRequestException::class,
        APIConnectionException::class,
        APIException::class
    )
    suspend fun completeAuthorizationSession(
        clientSecret: String,
        sessionId: String,
        publicToken: String? = null
    ): FinancialConnectionsAuthorizationSession

    @Throws(
        AuthenticationException::class,
        InvalidRequestException::class,
        APIConnectionException::class,
        APIException::class
    )
    suspend fun postMarkLinkingMoreAccounts(
        clientSecret: String
    ): FinancialConnectionsSessionManifest

    suspend fun cancelAuthorizationSession(
        clientSecret: String,
        sessionId: String
    ): FinancialConnectionsAuthorizationSession

    fun updateLocalManifest(
        block: (FinancialConnectionsSessionManifest) -> FinancialConnectionsSessionManifest
    )

    companion object {
        operator fun invoke(
            requestExecutor: FinancialConnectionsRequestExecutor,
            apiRequestFactory: ApiRequest.Factory,
            apiOptions: ApiRequest.Options,
            logger: Logger,
            locale: Locale,
            initialSync: SynchronizeSessionResponse?
        ): FinancialConnectionsManifestRepository =
            FinancialConnectionsManifestRepositoryImpl(
                requestExecutor,
                apiRequestFactory,
                apiOptions,
                locale,
                logger,
                initialSync
            )
    }
}

@Suppress("TooManyFunctions")
private class FinancialConnectionsManifestRepositoryImpl(
    val requestExecutor: FinancialConnectionsRequestExecutor,
    val apiRequestFactory: ApiRequest.Factory,
    val apiOptions: ApiRequest.Options,
    val locale: Locale,
    val logger: Logger,
    initialSync: SynchronizeSessionResponse?
) : FinancialConnectionsManifestRepository {

    /**
     * Ensures that manifest accesses via [getOrFetchSynchronizeFinancialConnectionsSession] suspend until
     * current writes are running.
     */
    val mutex = Mutex()
    private var cachedSynchronizeSessionResponse: SynchronizeSessionResponse? = initialSync

    override suspend fun getOrFetchSynchronizeFinancialConnectionsSession(
        clientSecret: String,
        applicationId: String
    ): SynchronizeSessionResponse = mutex.withLock {
        cachedSynchronizeSessionResponse ?: run {
            // fetch manifest and save it locally
            return requestExecutor.execute(
                synchronizeRequest(applicationId, clientSecret),
                SynchronizeSessionResponse.serializer()
            ).also { updateCachedSynchronizeSessionResponse("get/fetch", it) }
        }
    }

    override suspend fun synchronizeFinancialConnectionsSession(
        clientSecret: String,
        applicationId: String
    ): SynchronizeSessionResponse = mutex.withLock {
        val financialConnectionsRequest = synchronizeRequest(applicationId, clientSecret)
        return requestExecutor.execute(
            financialConnectionsRequest,
            SynchronizeSessionResponse.serializer()
        ).also { updateCachedSynchronizeSessionResponse("synchronize", it) }
    }

    private fun synchronizeRequest(
        applicationId: String,
        clientSecret: String
    ): ApiRequest = apiRequestFactory.createPost(
        url = synchronizeSessionUrl,
        options = apiOptions,
        params = mapOf(
            "expand" to listOf("manifest.active_auth_session"),
            "locale" to locale.toLanguageTag(),
            "mobile" to mapOf(
                PARAMS_FULLSCREEN to true,
                PARAMS_HIDE_CLOSE_BUTTON to true,
                NetworkConstants.PARAMS_APPLICATION_ID to applicationId
            ),
            NetworkConstants.PARAMS_CLIENT_SECRET to clientSecret
        )
    )

    override suspend fun markConsentAcquired(
        clientSecret: String
    ): FinancialConnectionsSessionManifest = mutex.withLock {
        val financialConnectionsRequest = apiRequestFactory.createPost(
            url = consentAcquiredUrl,
            options = apiOptions,
            params = mapOf(
                "expand" to listOf("active_auth_session"),
                NetworkConstants.PARAMS_CLIENT_SECRET to clientSecret
            )
        )
        return requestExecutor.execute(
            financialConnectionsRequest,
            FinancialConnectionsSessionManifest.serializer()
        ).also { updateCachedManifest("consent acquired", it) }
    }

    override suspend fun postAuthorizationSession(
        clientSecret: String,
        applicationId: String,
        institution: FinancialConnectionsInstitution
    ): FinancialConnectionsAuthorizationSession {
        val request = apiRequestFactory.createPost(
            url = FinancialConnectionsRepositoryImpl.authorizationSessionUrl,
            options = apiOptions,
            params = mapOf(
                NetworkConstants.PARAMS_CLIENT_SECRET to clientSecret,
                "use_mobile_handoff" to false,
                "use_abstract_flow" to true,
                "return_url" to "auth-redirect/$applicationId",
                "institution" to institution.id
            )
        )
        return requestExecutor.execute(
            request,
            FinancialConnectionsAuthorizationSession.serializer()
        ).also {
            updateActiveInstitution("postAuthorizationSession", institution)
            updateCachedActiveAuthSession("postAuthorizationSession", it)
        }
    }

    override suspend fun postAuthorizationSessionEvent(
        clientSecret: String,
        clientTimestamp: Date,
        sessionId: String,
        authSessionEvents: List<AuthSessionEvent>
    ): FinancialConnectionsAuthorizationSession {
        val request = apiRequestFactory.createPost(
            url = eventsAuthSessionUrl,
            options = apiOptions,
            params = mapOf(
                NetworkConstants.PARAMS_CLIENT_SECRET to clientSecret,
                "client_timestamp" to clientTimestamp.time.toString(),
                NetworkConstants.PARAMS_ID to sessionId,
            ) + authSessionEvents.mapIndexed { index, event ->
                "frontend_events[$index]" to event.toMap()
            }
        )
        return requestExecutor.execute(
            request,
            FinancialConnectionsAuthorizationSession.serializer()
        )
    }

    override suspend fun cancelAuthorizationSession(
        clientSecret: String,
        sessionId: String
    ): FinancialConnectionsAuthorizationSession {
        val request = apiRequestFactory.createPost(
            url = cancelAuthSessionUrl,
            options = apiOptions,
            params = mapOf(
                NetworkConstants.PARAMS_ID to sessionId,
                NetworkConstants.PARAMS_CLIENT_SECRET to clientSecret
            )
        )
        return requestExecutor.execute(
            request,
            FinancialConnectionsAuthorizationSession.serializer()
        ).also {
            updateCachedActiveAuthSession("cancelAuthorizationSession", it)
        }
    }

    override suspend fun completeAuthorizationSession(
        clientSecret: String,
        sessionId: String,
        publicToken: String?
    ): FinancialConnectionsAuthorizationSession {
        val request = apiRequestFactory.createPost(
            url = FinancialConnectionsRepositoryImpl.authorizeSessionUrl,
            options = apiOptions,
            params = mapOf(
                NetworkConstants.PARAMS_ID to sessionId,
                NetworkConstants.PARAMS_CLIENT_SECRET to clientSecret,
                "public_token" to publicToken
            ).filter { it.value != null }
        )
        return requestExecutor.execute(
            request,
            FinancialConnectionsAuthorizationSession.serializer()
        ).also {
            updateCachedActiveAuthSession("completeAuthorizationSession", it)
        }
    }

    override suspend fun postMarkLinkingMoreAccounts(
        clientSecret: String
    ): FinancialConnectionsSessionManifest {
        val request = apiRequestFactory.createPost(
            url = linkMoreAccountsUrl,
            options = apiOptions,
            params = mapOf(
                "expand" to listOf("active_auth_session"),
                NetworkConstants.PARAMS_CLIENT_SECRET to clientSecret
            )
        )
        return requestExecutor.execute(
            request,
            FinancialConnectionsSessionManifest.serializer()
        ).also {
            updateCachedManifest("postMarkLinkingMoreAccounts", it)
        }
    }

    override fun updateLocalManifest(
        block: (FinancialConnectionsSessionManifest) -> FinancialConnectionsSessionManifest
    ) {
        cachedSynchronizeSessionResponse?.manifest
            ?.let { block(it) }
            ?.let { updateCachedManifest("updateLocalManifest", it) }
    }

    private fun updateActiveInstitution(
        source: String,
        institution: FinancialConnectionsInstitution
    ) {
        logger.debug("SYNC_CACHE: updating local active institution from $source")
        cachedSynchronizeSessionResponse?.manifest?.copy(activeInstitution = institution)
            ?.let { updateCachedManifest("updating active institution", it) }
    }

    private fun updateCachedActiveAuthSession(
        source: String,
        authSession: FinancialConnectionsAuthorizationSession
    ) {
        logger.debug("SYNC_CACHE: updating local active auth session from $source")
        cachedSynchronizeSessionResponse?.manifest?.copy(activeAuthSession = authSession)
            ?.let { updateCachedManifest("updating active auth session", it) }
    }

    private fun updateCachedSynchronizeSessionResponse(
        source: String,
        synchronizeSessionResponse: SynchronizeSessionResponse
    ) {
        logger.debug("SYNC_CACHE: updating local sync object from $source")
        cachedSynchronizeSessionResponse = synchronizeSessionResponse
    }

    private fun updateCachedManifest(
        source: String,
        manifest: FinancialConnectionsSessionManifest
    ) {
        logger.debug("SYNC_CACHE: updating local manifest from $source")
        cachedSynchronizeSessionResponse = cachedSynchronizeSessionResponse?.copy(
            manifest = manifest
        )
    }

    companion object {
        internal const val PARAMS_FULLSCREEN = "fullscreen"
        internal const val PARAMS_HIDE_CLOSE_BUTTON = "hide_close_button"

        internal const val synchronizeSessionUrl: String =
            "${ApiRequest.API_HOST}/v1/financial_connections/sessions/synchronize"

        internal const val cancelAuthSessionUrl: String =
            "${ApiRequest.API_HOST}/v1/connections/auth_sessions/cancel"

        internal const val eventsAuthSessionUrl: String =
            "${ApiRequest.API_HOST}/v1/connections/auth_sessions/events"

        internal const val consentAcquiredUrl: String =
            "${ApiRequest.API_HOST}/v1/link_account_sessions/consent_acquired"

        internal const val linkMoreAccountsUrl: String =
            "${ApiRequest.API_HOST}/v1/link_account_sessions/link_more_accounts"
    }
}
