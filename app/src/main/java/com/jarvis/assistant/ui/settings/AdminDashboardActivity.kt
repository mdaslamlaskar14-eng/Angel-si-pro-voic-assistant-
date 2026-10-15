package com.jarvis.assistant.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.storage.FirebaseStorage
import com.jarvis.assistant.R
import kotlinx.coroutines.tasks.await
import com.jarvis.assistant.data.model.PaymentSubmission
import com.jarvis.assistant.databinding.ActivityAdminDashboardBinding
import com.jarvis.assistant.util.ThemeManager
import kotlinx.coroutines.flow.collectLatest

class AdminDashboardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAdminDashboardBinding
    private lateinit var viewModel: AdminViewModel
    private lateinit var adapter: SubmissionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applyTheme(this)
        binding = ActivityAdminDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemeManager.applyDynamicBackground(binding.root)

        viewModel = ViewModelProvider(this)[AdminViewModel::class.java]

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupRecyclerView()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        adapter = SubmissionAdapter(
            onApprove = { viewModel.approve(it) },
            onReject = { showRejectDialog(it) },
            onViewReceipt = { showReceipt(it) }
        )
        binding.rvSubmissions.layoutManager = LinearLayoutManager(this)
        binding.rvSubmissions.adapter = adapter
    }

    private fun observeViewModel() {
        lifecycleScope.launchWhenStarted {
            viewModel.pendingSubmissions.collectLatest { subs ->
                adapter.submitList(subs)
                binding.tvEmpty.visibility = if (subs.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launchWhenStarted {
            viewModel.isLoading.collectLatest { loading ->
                binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showRejectDialog(submission: PaymentSubmission) {
        val input = android.widget.EditText(this).apply {
            hint = "Reason for rejection"
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Reject Payment")
            .setView(input)
            .setPositiveButton("Reject") { _, _ ->
                val reason = input.text.toString().trim()
                if (reason.isNotEmpty()) viewModel.reject(submission, reason)
                else Toast.makeText(this, "Reason required", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showReceipt(submission: PaymentSubmission) {
        val imgView = ImageView(this)
        imgView.scaleType = ImageView.ScaleType.FIT_CENTER
        
        val storageRef = FirebaseStorage.getInstance().reference.child(submission.receiptStoragePath)
        
        lifecycleScope.launchWhenStarted {
            try {
                // We'll use a simple byte array download for the dialog
                val bytes = storageRef.getBytes(1024 * 1024 * 5).await() // 5MB max
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                imgView.setImageBitmap(bitmap)
                
                MaterialAlertDialogBuilder(this@AdminDashboardActivity)
                    .setTitle("Receipt: ${submission.transactionReference}")
                    .setView(imgView)
                    .setPositiveButton("Close", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(this@AdminDashboardActivity, "Failed to load receipt: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private class SubmissionAdapter(
        private val onApprove: (PaymentSubmission) -> Unit,
        private val onReject: (PaymentSubmission) -> Unit,
        private val onViewReceipt: (PaymentSubmission) -> Unit
    ) : RecyclerView.Adapter<SubmissionAdapter.ViewHolder>() {

        private var items = emptyList<PaymentSubmission>()

        fun submitList(newList: List<PaymentSubmission>) {
            items = newList
            notifyDataSetChanged()
        }

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvOrder: TextView = view.findViewById(R.id.tvSubOrderId)
            val tvTrans: TextView = view.findViewById(R.id.tvSubTransId)
            val tvUser: TextView = view.findViewById(R.id.tvSubUserEmail)
            val btnView: MaterialButton = view.findViewById(R.id.btnViewReceipt)
            val btnApprove: MaterialButton = view.findViewById(R.id.btnApprove)
            val btnReject: MaterialButton = view.findViewById(R.id.btnReject)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_payment_submission, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val sub = items[position]
            holder.tvOrder.text = "Order ID: ${sub.orderId}"
            holder.tvTrans.text = "Trans ID: ${sub.transactionReference}"
            holder.tvUser.text = "User ID: ${sub.userId}" // In real app, fetch email

            holder.btnView.setOnClickListener { onViewReceipt(sub) }
            holder.btnApprove.setOnClickListener { onApprove(sub) }
            holder.btnReject.setOnClickListener { onReject(sub) }
        }

        override fun getItemCount() = items.size
    }
}
