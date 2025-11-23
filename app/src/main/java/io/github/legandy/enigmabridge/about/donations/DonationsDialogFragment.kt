package io.github.legandy.enigmabridge.about.donations

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import io.github.legandy.enigmabridge.R
import io.github.legandy.enigmabridge.databinding.FragmentDonationsDialogBinding

class DonationsDialogFragment : DialogFragment() {

    private var _binding: FragmentDonationsDialogBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDonationsDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val donationOptions = listOf(
            DonationOption(
                titleResId = R.string.donation_option_buymeacoffee,
                descriptionResId = R.string.donation_desc_buymeacoffee,
                url = "https://www.buymeacoffee.com/legandy"
            ),
            DonationOption(
                titleResId = R.string.donation_option_patreon,
                descriptionResId = R.string.donation_desc_patreon,
                url = "https://www.patreon.com/your_patreon" // Replace with your Patreon link
            ),
            DonationOption(
                titleResId = R.string.donation_option_liberapay,
                descriptionResId = R.string.donation_desc_liberapay,
                url = "https://liberapay.com/your_liberapay" // Replace with your Liberapay link
            ),
            DonationOption(
                titleResId = R.string.donation_option_paypal,
                descriptionResId = R.string.donation_desc_paypal,
                url = "https://paypal.me/your_paypal" // Replace with your PayPal.me link
            )
        )

        binding.donationsRecyclerView.adapter = DonationOptionsAdapter(donationOptions) {
            openLink(it)
            dismiss()
        }

        // Set up the dialog title
        binding.dialogTitle.text = getString(R.string.donations)
    }

    private fun openLink(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
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
