package io.github.legandy.enigmabridge.ui.about

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import io.github.legandy.enigmabridge.R
import io.github.legandy.enigmabridge.databinding.ActivityAboutBinding
import io.github.legandy.enigmabridge.ui.donations.DonationsDialogFragment

// Screen for app info
class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.itemCreator.setOnClickListener {
            openLink(getString(R.string.creator_github_link).toUri())
        }

        binding.itemSource.setOnClickListener {
            openLink(getString(R.string.source_code_github_link).toUri())
        }

        binding.itemLicense.setOnClickListener {
            openLink(getString(R.string.license_link).toUri())
        }

        binding.itemDonations.setOnClickListener {
            DonationsDialogFragment().show(
                supportFragmentManager,
                DonationsDialogFragment.TAG
            )
        }

        try {
            val pInfo =
                applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0)
            val versionName = pInfo.versionName
            binding.aboutVersionValue.text = versionName
        } catch (e: Exception) {
            e.printStackTrace()
            binding.aboutVersionValue.text = getString(R.string.version_unknown)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun openLink(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW, uri)
        startActivity(intent)
    }
}