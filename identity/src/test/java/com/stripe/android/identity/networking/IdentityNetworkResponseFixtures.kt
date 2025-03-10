package com.stripe.android.identity.networking

internal val VERIFICATION_PAGE_DATA_JSON_STRING = """
    {
      "id": "vs_1KWvnMEAjaOkiuGMvfNAA0vo",
      "object": "identity.verification_page_data",
      "requirements": {
        "errors": [

        ],
        "missing": [
          "id_document_front",
          "id_document_back",
          "id_document_type"
        ]
      },
      "status": "requires_input",
      "submitted": false
    }
""".trimIndent()

internal val VERIFICATION_PAGE_NOT_REQUIRE_LIVE_CAPTURE_JSON_STRING = """
    {
      "id": "vs_1KgNstEAjaOkiuGMpFXVTocU",
      "object": "identity.verification_page",
      "biometric_consent": {
        "accept_button_text": "Accept and continue",
        "body": "\u003Cp\u003E\u003Cb\u003EHow Stripe will verify your identity\u003C/b\u003E\u003C/p\u003E\u003Cp\u003EStripe will use biometric technology (on images of you and your IDs) and other data sources to confirm your identity and for fraud and security purposes. Stripe will store these images and the results of this check and share them with mlgb.band.\u003C/p\u003E\u003Cp\u003E\u003Ca href='https://stripe.com/about'\u003ELearn about Stripe\u003C/a\u003E\u003C/p\u003E\u003Cp\u003E\u003Ca href='https://stripe.com/privacy-center/legal#stripe-identity'\u003ELearn how Stripe Identity works\u003C/a\u003E\u003C/p\u003E",
        "decline_button_text": "No, don't verify",
        "privacy_policy": "Data will be stored and may be used according to the \u003Ca href='https://stripe.com/privacy'\u003EStripe Privacy Policy\u003C/a\u003E and mlgb.band Privacy Policy.",
        "time_estimate": "Takes about 1–2 minutes.",
        "title": "mlgb.band uses Stripe to verify your identity",
        "scroll_to_continue_button_text": "Scroll to consent"
      },
      "document_capture": {
        "autocapture_timeout": 8000,
        "file_purpose": "identity_private",
        "high_res_image_compression_quality": 0.92,
        "high_res_image_crop_padding": 0.08,
        "high_res_image_max_dimension": 3000,
        "low_res_image_compression_quality": 0.82,
        "low_res_image_max_dimension": 3000,
        "models": {
          "id_detector_min_iou": 0.8,
          "id_detector_min_score": 0.8,
          "id_detector_url": "https://b.stripecdn.com/gelato-statics-srv/assets/945cd2bb8681a56dd5b0344a009a1f2416619382/assets/id_detectors/tflite/2022-02-23/model.tflite"
        },
        "motion_blur_min_duration": 500,
        "motion_blur_min_iou": 0.95,
        "require_live_capture": false
      },
      "document_select": {
        "body": null,
        "button_text": "Next",
        "id_document_type_allowlist": {
          "passport": "Passport",
          "driving_license": "Driver's license",
          "id_card": "Identity card"
        },
        "title": "Which form of identification do you want to use?"
      },
      "fallback_url": "https://verify.stripe.com/start/test_YWNjdF8xSU84aDNFQWphT2tpdUdNLF9MTjg5dFZtRWV1T1c1QXBxMkJ6MTUwZlI5c3JtTE5U0100BzkFlqqD",
      "livemode": false,
      "requirements": {
        "missing": [
          "biometric_consent",
          "id_document_front",
          "id_document_back",
          "id_document_type"
        ]
      },
      "status": "requires_input",
      "submitted": false,
      "success": {
        "body": "\u003Cp\u003E\u003Cb\u003EThank you for providing your information\u003C/b\u003E\u003C/p\u003E\u003Cp\u003Emlgb.band will reach out if additional details are required.\u003C/p\u003E\u003Cp\u003E\u003Cb\u003ENext steps\u003C/b\u003E\u003C/p\u003E\u003Cp\u003Emlgb.band will contact you regarding the outcome of your identification process.\u003C/p\u003E\u003Cp\u003E\u003Cb\u003EMore about Stripe Identity\u003C/b\u003E\u003C/p\u003E\u003Cp\u003E\u003Ca href='https://support.stripe.com/questions/common-questions-about-stripe-identity'\u003ECommon questions about Stripe Identity\u003C/a\u003E\u003C/p\u003E\u003Cp\u003E\u003Ca href='https://stripe.com/privacy-center/legal#stripe-identity'\u003ELearn how Stripe uses data\u003C/a\u003E\u003C/p\u003E\u003Cp\u003E\u003Ca href='https://stripe.com/privacy'\u003EStripe Privacy Policy\u003C/a\u003E\u003C/p\u003E\u003Cp\u003E\u003Ca href='mailto:privacy@stripe.com'\u003EContact Stripe\u003C/a\u003E\u003C/p\u003E",
        "button_text": "Complete",
        "title": "Verification pending"
      },
      "unsupported_client": false
    }
""".trimIndent()

internal val VERIFICATION_PAGE_REQUIRE_LIVE_CAPTURE_JSON_STRING = """
    {
      "id": "vs_1KgNstEAjaOkiuGMpFXVTocU",
      "object": "identity.verification_page",
      "biometric_consent": {
        "accept_button_text": "Accept and continue",
        "body": "\u003Cp\u003E\u003Cb\u003EHow Stripe will verify your identity\u003C/b\u003E\u003C/p\u003E\u003Cp\u003EStripe will use biometric technology (on images of you and your IDs) and other data sources to confirm your identity and for fraud and security purposes. Stripe will store these images and the results of this check and share them with mlgb.band.\u003C/p\u003E\u003Cp\u003E\u003Ca href='https://stripe.com/about'\u003ELearn about Stripe\u003C/a\u003E\u003C/p\u003E\u003Cp\u003E\u003Ca href='https://stripe.com/privacy-center/legal#stripe-identity'\u003ELearn how Stripe Identity works\u003C/a\u003E\u003C/p\u003E",
        "decline_button_text": "No, don't verify",
        "privacy_policy": "Data will be stored and may be used according to the \u003Ca href='https://stripe.com/privacy'\u003EStripe Privacy Policy\u003C/a\u003E and mlgb.band Privacy Policy.",
        "time_estimate": "Takes about 1–2 minutes.",
        "title": "mlgb.band uses Stripe to verify your identity",
        "scroll_to_continue_button_text": "Scroll to consent"
      },
      "document_capture": {
        "autocapture_timeout": 8000,
        "file_purpose": "identity_private",
        "high_res_image_compression_quality": 0.92,
        "high_res_image_crop_padding": 0.08,
        "high_res_image_max_dimension": 3000,
        "low_res_image_compression_quality": 0.82,
        "low_res_image_max_dimension": 3000,
        "models": {
          "id_detector_min_iou": 0.8,
          "id_detector_min_score": 0.8,
          "id_detector_url": "https://b.stripecdn.com/gelato-statics-srv/assets/945cd2bb8681a56dd5b0344a009a1f2416619382/assets/id_detectors/tflite/2022-02-23/model.tflite"
        },
        "motion_blur_min_duration": 500,
        "motion_blur_min_iou": 0.95,
        "require_live_capture": true
      },
      "document_select": {
        "body": null,
        "button_text": "Next",
        "id_document_type_allowlist": {
          "passport": "Passport",
          "driving_license": "Driver's license",
          "id_card": "Identity card"
        },
        "title": "Which form of identification do you want to use?"
      },
      "fallback_url": "https://verify.stripe.com/start/test_YWNjdF8xSU84aDNFQWphT2tpdUdNLF9MTjg5dFZtRWV1T1c1QXBxMkJ6MTUwZlI5c3JtTE5U0100BzkFlqqD",
      "livemode": false,
      "requirements": {
        "missing": [
          "biometric_consent",
          "id_document_front",
          "id_document_back",
          "id_document_type"
        ]
      },
      "status": "requires_input",
      "submitted": false,
      "success": {
        "body": "\u003Cp\u003E\u003Cb\u003EThank you for providing your information\u003C/b\u003E\u003C/p\u003E\u003Cp\u003Emlgb.band will reach out if additional details are required.\u003C/p\u003E\u003Cp\u003E\u003Cb\u003ENext steps\u003C/b\u003E\u003C/p\u003E\u003Cp\u003Emlgb.band will contact you regarding the outcome of your identification process.\u003C/p\u003E\u003Cp\u003E\u003Cb\u003EMore about Stripe Identity\u003C/b\u003E\u003C/p\u003E\u003Cp\u003E\u003Ca href='https://support.stripe.com/questions/common-questions-about-stripe-identity'\u003ECommon questions about Stripe Identity\u003C/a\u003E\u003C/p\u003E\u003Cp\u003E\u003Ca href='https://stripe.com/privacy-center/legal#stripe-identity'\u003ELearn how Stripe uses data\u003C/a\u003E\u003C/p\u003E\u003Cp\u003E\u003Ca href='https://stripe.com/privacy'\u003EStripe Privacy Policy\u003C/a\u003E\u003C/p\u003E\u003Cp\u003E\u003Ca href='mailto:privacy@stripe.com'\u003EContact Stripe\u003C/a\u003E\u003C/p\u003E",
        "button_text": "Complete",
        "title": "Verification pending"
      },
      "unsupported_client": false
    }
""".trimIndent()

internal val VERIFICATION_PAGE_REQUIRE_SELFIE_LIVE_CAPTURE_JSON_STRING = """
    {
      "id": "vs_1M8UU5GMZYGNxJkBN55D3nva",
      "object": "identity.verification_page",
      "biometric_consent": {
        "accept_button_text": "Accept and continue",
        "body": "<p><b>How Stripe will verify your identity</b></p><p><a href='https://stripe.com/about'>Stripe</a> will use biometric technology (on images of you and your IDs), as well as other data sources and our service providers, to confirm your identity and for fraud and security purposes. Stripe will store these images and the results of this check and share them with Andrew's Audio. You can subsequently opt-out by contacting Stripe. <a href='https://stripe.com/privacy-center/legal#stripe-identity'>Learn more</a></p>",
        "decline_button_text": "No, don't verify",
        "privacy_policy": "Data will be stored and may be used according to the <a href='https://stripe.com/privacy'>Stripe Privacy Policy</a> and Andrew's Audio Privacy Policy.",
        "scroll_to_continue_button_text": "Scroll to continue",
        "time_estimate": "Takes about 1–2 minutes.",
        "title": "Andrew's Audio uses Stripe to verify your identity"
      },
      "document_capture": {
        "autocapture_timeout": 8000,
        "file_purpose": "identity_private",
        "high_res_image_compression_quality": 0.92,
        "high_res_image_crop_padding": 0.08,
        "high_res_image_max_dimension": 3000,
        "ios_id_card_back_barcode_timeout": 3000,
        "ios_id_card_back_country_barcode_symbologies": {
          "CA": "pdf417",
          "US": "pdf417"
        },
        "low_res_image_compression_quality": 0.82,
        "low_res_image_max_dimension": 3000,
        "models": {
          "id_detector_min_iou": 0.8,
          "id_detector_min_score": 0.5,
          "id_detector_url": "https://b.stripecdn.com/gelato-statics-srv/assets/d137be6ecc86477800ea4ef82154174092dc4c16/assets/id_detectors/tflite/2022-08-19/model.tflite"
        },
        "motion_blur_min_duration": 500,
        "motion_blur_min_iou": 0.95,
        "require_live_capture": true
      },
      "document_select": {
        "body": null,
        "button_text": "Next",
        "id_document_type_allowlist": {
          "driving_license": "Driver's license",
          "id_card": "Identity card",
          "passport": "Passport"
        },
        "title": "Which form of identification do you want to use?"
      },
      "fallback_url": "https://verify.stripe.com/start/live_YWNjdF8xSDM0ZFhHTVpZR054SmtCLF9Nc0V5NkI2TjZ6MkZPWUxsUndtMXlyZzA5YzdDdjVU0100gEzqe9Je",
      "livemode": true,
      "requirements": {
        "missing": [
          "biometric_consent",
          "face",
          "id_document_front",
          "id_document_back",
          "id_document_type"
        ]
      },
      "selfie": {
        "autocapture_timeout": 8000,
        "file_purpose": "identity_private",
        "high_res_image_compression_quality": 0.92,
        "high_res_image_crop_padding": 0.5,
        "high_res_image_max_dimension": 1440,
        "low_res_image_compression_quality": 0.82,
        "low_res_image_max_dimension": 800,
        "max_centered_threshold_x": 0.2,
        "max_centered_threshold_y": 0.2,
        "max_coverage_threshold": 0.8,
        "min_coverage_threshold": 0.07,
        "min_edge_threshold": 0.05,
        "models": {
          "face_detector_min_iou": 0.8,
          "face_detector_min_score": 0.8,
          "face_detector_url": "https://b.stripecdn.com/gelato-statics-srv/assets/a8bcf0129dcd29084f6797ede7e0be86f9e11ed5/assets/face_detectors/tflite/2022-05-23/model.tflite"
        },
        "num_samples": 8,
        "sample_interval": 250,
        "training_consent_text": "Allow Stripe to use your images to improve our biometric verification technology. You can remove Stripe's permissions at any time by <a href='mailto:privacy@stripe.com'>contacting Stripe</a>. <a href='https://stripe.com/privacy-center/legal#stripe-identity'>Learn how Stripe uses data</a>"
      },
      "status": "requires_input",
      "submitted": false,
      "success": {
        "body": "<p>Thank you for providing your information. Andrew's Audio will reach out if additional details are required.</p><p><b>Next steps</b></p><p>Andrew's Audio will contact you regarding the outcome of your identification process.</p><p><b>More about Stripe Identity</b></p><p><a href='https://support.stripe.com/questions/common-questions-about-stripe-identity'>Common questions about Stripe Identity</a></p><p><a href='https://stripe.com/privacy-center/legal#stripe-identity'>Learn how Stripe uses data</a></p><p><a href='https://stripe.com/privacy'>Stripe Privacy Policy</a></p><p><a href='mailto:privacy@stripe.com'>Contact Stripe</a></p>",
        "button_text": "Complete",
        "title": "Verification submitted"
      },
      "unsupported_client": false
    }
""".trimIndent()

internal val ERROR_JSON_STRING = """
    {
      "error": {
        "code": "resource_missing",
        "doc_url": "https://stripe.com/docs/error-codes/resource-missing",
        "message": "No such file upload: 'hightResImage'",
        "param": "collected_data[id_document][front][high_res_image]",
        "type": "invalid_request_error"
      }
    }

""".trimIndent()

internal val FILE_UPLOAD_SUCCESS_JSON_STRING = """
    {
      "id": "file_1KZUtnEAjaOkiuGM9AuSSXXO",
      "object": "file",
      "created": 1646376359,
      "expires_at": null,
      "filename": "initialScreen.png",
      "purpose": "identity_private",
      "size": 136000,
      "title": null,
      "type": "png",
      "url": null
    }
""".trimIndent()
