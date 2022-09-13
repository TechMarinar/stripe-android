package com.stripe.android.paymentsheet.example

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.stripe.android.core.version.StripeSdkVersion
import com.stripe.android.paymentsheet.example.samples.activity.LaunchPaymentSheetCompleteActivity
import com.stripe.android.paymentsheet.example.samples.activity.LaunchPaymentSheetCustomActivity
import com.stripe.android.paymentsheet.example.playground.activity.PaymentSheetPlaygroundActivity

import com.stripe.android.paymentsheet.example.databinding.ActivityMainBinding
import com.stripe.android.paymentsheet.example.devtools.DevToolsBottomSheetDialogFragment
import com.stripe.android.paymentsheet.example.playground.activity.AppearancePlaygroundActivity

class MainActivity : AppCompatActivity() {
    private val viewBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)
        setSupportActionBar(viewBinding.toolbar)

        viewBinding.launchCompleteButton.setOnClickListener {
            startActivity(Intent(this, LaunchPaymentSheetCompleteActivity::class.java))
        }

        viewBinding.launchCustomButton.setOnClickListener {
            startActivity(Intent(this, LaunchPaymentSheetCustomActivity::class.java))
        }

        viewBinding.launchPlaygroundButton.setOnClickListener {
            startActivity(Intent(this, PaymentSheetPlaygroundActivity::class.java))
        }

        viewBinding.appearanceButton.setOnClickListener {
            startActivity(Intent(this, AppearancePlaygroundActivity::class.java))
        }

        viewBinding.devTools.setOnClickListener {
            val bottomSheet = DevToolsBottomSheetDialogFragment.newInstance()
            bottomSheet.show(supportFragmentManager, bottomSheet.tag)
        }

        viewBinding.version.text = StripeSdkVersion.VERSION_NAME
    }
}
