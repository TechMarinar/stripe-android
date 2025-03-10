package com.stripe.android.link.repositories

import com.stripe.android.core.injection.IOContext
import com.stripe.android.core.injection.PUBLISHABLE_KEY
import com.stripe.android.core.injection.STRIPE_ACCOUNT_ID
import com.stripe.android.core.networking.ApiRequest
import com.stripe.android.link.LinkPaymentDetails
import com.stripe.android.link.confirmation.ConfirmStripeIntentParamsFactory
import com.stripe.android.link.ui.paymentmethod.SupportedPaymentMethod
import com.stripe.android.model.ConsumerPaymentDetails
import com.stripe.android.model.ConsumerPaymentDetailsCreateParams
import com.stripe.android.model.ConsumerPaymentDetailsUpdateParams
import com.stripe.android.model.ConsumerSession
import com.stripe.android.model.ConsumerSessionLookup
import com.stripe.android.model.ConsumerSignUpConsentAction
import com.stripe.android.model.FinancialConnectionsSession
import com.stripe.android.model.PaymentMethodCreateParams
import com.stripe.android.model.StripeIntent
import com.stripe.android.networking.StripeRepository
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

/**
 * Repository that uses [StripeRepository] for Link services.
 */
@Singleton
internal class LinkApiRepository @Inject constructor(
    @Named(PUBLISHABLE_KEY) private val publishableKeyProvider: () -> String,
    @Named(STRIPE_ACCOUNT_ID) private val stripeAccountIdProvider: () -> String?,
    private val stripeRepository: StripeRepository,
    @IOContext private val workContext: CoroutineContext,
    private val locale: Locale?
) : LinkRepository {

    override suspend fun lookupConsumer(
        email: String?,
        authSessionCookie: String?
    ): Result<ConsumerSessionLookup> = withContext(workContext) {
        runCatching {
            requireNotNull(
                stripeRepository.lookupConsumerSession(
                    email,
                    authSessionCookie,
                    ApiRequest.Options(
                        publishableKeyProvider(),
                        stripeAccountIdProvider()
                    )
                )
            )
        }
    }

    override suspend fun consumerSignUp(
        email: String,
        phone: String,
        country: String,
        name: String?,
        authSessionCookie: String?,
        consentAction: ConsumerSignUpConsentAction
    ): Result<ConsumerSession> = withContext(workContext) {
        runCatching {
            requireNotNull(
                stripeRepository.consumerSignUp(
                    email,
                    phone,
                    country,
                    name,
                    locale,
                    authSessionCookie,
                    consentAction,
                    ApiRequest.Options(
                        publishableKeyProvider(),
                        stripeAccountIdProvider()
                    )
                )
            )
        }
    }

    override suspend fun startVerification(
        consumerSessionClientSecret: String,
        consumerPublishableKey: String?,
        authSessionCookie: String?
    ): Result<ConsumerSession> = withContext(workContext) {
        runCatching {
            requireNotNull(
                stripeRepository.startConsumerVerification(
                    consumerSessionClientSecret,
                    locale ?: Locale.US,
                    authSessionCookie,
                    consumerPublishableKey?.let {
                        ApiRequest.Options(it)
                    } ?: ApiRequest.Options(
                        publishableKeyProvider(),
                        stripeAccountIdProvider()
                    )
                )
            )
        }
    }

    override suspend fun confirmVerification(
        verificationCode: String,
        consumerSessionClientSecret: String,
        consumerPublishableKey: String?,
        authSessionCookie: String?
    ): Result<ConsumerSession> = withContext(workContext) {
        runCatching {
            requireNotNull(
                stripeRepository.confirmConsumerVerification(
                    consumerSessionClientSecret,
                    verificationCode,
                    authSessionCookie,
                    consumerPublishableKey?.let {
                        ApiRequest.Options(it)
                    } ?: ApiRequest.Options(
                        publishableKeyProvider(),
                        stripeAccountIdProvider()
                    )
                )
            )
        }
    }

    override suspend fun logout(
        consumerSessionClientSecret: String,
        consumerPublishableKey: String?,
        authSessionCookie: String?
    ): Result<ConsumerSession> = withContext(workContext) {
        runCatching {
            requireNotNull(
                stripeRepository.logoutConsumer(
                    consumerSessionClientSecret,
                    authSessionCookie,
                    consumerPublishableKey?.let {
                        ApiRequest.Options(it)
                    } ?: ApiRequest.Options(
                        publishableKeyProvider(),
                        stripeAccountIdProvider()
                    )
                )
            )
        }
    }

    override suspend fun listPaymentDetails(
        consumerSessionClientSecret: String,
        consumerPublishableKey: String?
    ): Result<ConsumerPaymentDetails> = withContext(workContext) {
        runCatching {
            requireNotNull(
                stripeRepository.listPaymentDetails(
                    consumerSessionClientSecret,
                    SupportedPaymentMethod.allTypes,
                    consumerPublishableKey?.let {
                        ApiRequest.Options(it)
                    } ?: ApiRequest.Options(
                        publishableKeyProvider(),
                        stripeAccountIdProvider()
                    )
                )
            )
        }
    }

    override suspend fun createFinancialConnectionsSession(
        consumerSessionClientSecret: String,
        consumerPublishableKey: String?
    ): Result<FinancialConnectionsSession> = withContext(workContext) {
        runCatching {
            requireNotNull(
                stripeRepository.createLinkFinancialConnectionsSession(
                    consumerSessionClientSecret,
                    consumerPublishableKey?.let {
                        ApiRequest.Options(it)
                    } ?: ApiRequest.Options(
                        publishableKeyProvider(),
                        stripeAccountIdProvider()
                    )
                )
            )
        }
    }

    override suspend fun createBankAccountPaymentDetails(
        financialConnectionsAccountId: String,
        consumerSessionClientSecret: String,
        consumerPublishableKey: String?
    ): Result<ConsumerPaymentDetails.BankAccount> = withContext(workContext) {
        runCatching {
            requireNotNull(
                stripeRepository.createPaymentDetails(
                    consumerSessionClientSecret,
                    financialConnectionsAccountId,
                    consumerPublishableKey?.let {
                        ApiRequest.Options(it)
                    } ?: ApiRequest.Options(
                        publishableKeyProvider(),
                        stripeAccountIdProvider()
                    )
                )?.paymentDetails?.first()?.let {
                    it as? ConsumerPaymentDetails.BankAccount
                }
            )
        }
    }

    override suspend fun createCardPaymentDetails(
        paymentMethodCreateParams: PaymentMethodCreateParams,
        userEmail: String,
        stripeIntent: StripeIntent,
        consumerSessionClientSecret: String,
        consumerPublishableKey: String?
    ): Result<LinkPaymentDetails.New> = withContext(workContext) {
        runCatching {
            requireNotNull(
                stripeRepository.createPaymentDetails(
                    consumerSessionClientSecret,
                    ConsumerPaymentDetailsCreateParams.Card(
                        paymentMethodCreateParams.toParamMap(),
                        userEmail
                    ),
                    consumerPublishableKey?.let {
                        ApiRequest.Options(it)
                    } ?: ApiRequest.Options(
                        publishableKeyProvider(),
                        stripeAccountIdProvider()
                    )
                )?.paymentDetails?.first()?.let {
                    LinkPaymentDetails.New(
                        it,
                        ConfirmStripeIntentParamsFactory.createFactory(stripeIntent)
                            .createPaymentMethodCreateParams(
                                consumerSessionClientSecret,
                                it,
                                ConsumerPaymentDetailsCreateParams.Card.extraConfirmationParams(
                                    paymentMethodCreateParams
                                )
                            ),
                        paymentMethodCreateParams
                    )
                }
            )
        }
    }

    override suspend fun updatePaymentDetails(
        updateParams: ConsumerPaymentDetailsUpdateParams,
        consumerSessionClientSecret: String,
        consumerPublishableKey: String?
    ): Result<ConsumerPaymentDetails> = withContext(workContext) {
        runCatching {
            requireNotNull(
                stripeRepository.updatePaymentDetails(
                    consumerSessionClientSecret,
                    updateParams,
                    consumerPublishableKey?.let {
                        ApiRequest.Options(it)
                    } ?: ApiRequest.Options(
                        publishableKeyProvider(),
                        stripeAccountIdProvider()
                    )
                )
            )
        }
    }

    override suspend fun deletePaymentDetails(
        paymentDetailsId: String,
        consumerSessionClientSecret: String,
        consumerPublishableKey: String?
    ): Result<Unit> = withContext(workContext) {
        runCatching {
            stripeRepository.deletePaymentDetails(
                consumerSessionClientSecret,
                paymentDetailsId,
                consumerPublishableKey?.let {
                    ApiRequest.Options(it)
                } ?: ApiRequest.Options(
                    publishableKeyProvider(),
                    stripeAccountIdProvider()
                )
            )
        }
    }
}
