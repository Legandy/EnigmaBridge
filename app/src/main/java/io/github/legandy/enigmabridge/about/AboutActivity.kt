package io.github.legandy.enigmabridge.about

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.github.legandy.enigmabridge.R
import io.github.legandy.enigmabridge.databinding.ActivityAboutBinding
import io.github.legandy.enigmabridge.about.donations.DonationsDialogFragment

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Set up click listeners for the items
        binding.itemCreator.setOnClickListener {
            openLink(Uri.parse(getString(R.string.creator_github_link)))
        }

        binding.itemSource.setOnClickListener {
            openLink(Uri.parse(getString(R.string.source_code_github_link)))
        }

        binding.itemLicense.setOnClickListener {
            openLink(Uri.parse(getString(R.string.license_link)))
        }

        binding.itemDonations.setOnClickListener {
            DonationsDialogFragment().show(supportFragmentManager, DonationsDialogFragment.TAG)
        }

        // Set the app version
        try {
            val pInfo = applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0)
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
