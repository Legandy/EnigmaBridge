package io.github.legandy.enigmabridge.about.donations

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import io.github.legandy.enigmabridge.R
import io.github.legandy.enigmabridge.databinding.FragmentDonationsDialogBinding
import androidx.core.net.toUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class DonationsDialogFragment : DialogFragment() {

    private var _binding: FragmentDonationsDialogBinding? = null
    private val binding get() = _binding!!
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = FragmentDonationsDialogBinding.inflate(layoutInflater)

        val donationOptions = listOf(
            DonationOption(
                titleResId = R.string.donation_option_buymeacoffee,
                descriptionResId = R.string.donation_desc_buymeacoffee,
                urlResId = R.string.link_buymeacoffee,
            ),
            DonationOption(
                titleResId = R.string.donation_option_kofi,
                descriptionResId = R.string.donation_desc_kofi,
                urlResId = R.string.link_kofi,
            ),
            DonationOption(
                titleResId = R.string.donation_option_paypal,
                descriptionResId = R.string.donation_desc_paypal,
                urlResId = R.string.link_paypal,
            )
        )
        
        binding.donationsRecyclerView.adapter = DonationOptionsAdapter(donationOptions) {
            openLink(it)
            dismiss()
        }
        
        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()
    }

    private fun openLink(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.toast_no_browser_found, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "DonationsDialogFragment"
    }
}
