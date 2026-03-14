package io.github.legandy.enigmabridge.ui.donations

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.net.toUri
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.legandy.enigmabridge.R
import io.github.legandy.enigmabridge.databinding.FragmentDonationsDialogBinding

// Dialog for displaying donation options
class DonationsDialogFragment : DialogFragment() {

    private var _binding: FragmentDonationsDialogBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = FragmentDonationsDialogBinding.inflate(layoutInflater)

        val donationOptions = listOf(
            DonationOption(
                titleResId = R.string.donation_option_kofi,
                descriptionResId = R.string.donation_desc_kofi,
                urlResId = R.string.link_kofi,
            ), DonationOption(
                titleResId = R.string.donation_option_githubsponsors,
                descriptionResId = R.string.donation_desc_githubsponsors,
                urlResId = R.string.link_githubsponsors,
            )
        )

        binding.donationsRecyclerView.adapter =
            DonationOptionsAdapter(options = donationOptions, onItemClick = { url ->
                openLink(url)
                dismiss()
            }, onLongItemClick = { url ->
                copyToClipboard(url)
            })

        return MaterialAlertDialogBuilder(requireContext()).setView(binding.root).create()
    }

    private fun openLink(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.toast_no_browser_found, Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyToClipboard(url: String) {
        val clipboard =
            requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Donation Link", url)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, R.string.toast_link_copied, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "DonationsDialogFragment"
    }
}
