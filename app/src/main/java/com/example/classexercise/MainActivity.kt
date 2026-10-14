package com.example.classexercise

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.classexercise.databinding.ActivityMainBinding
import com.example.classexercise.databinding.DialogEventDetailsBinding
import com.example.classexercise.databinding.ItemEventBinding
import com.example.classexercise.databinding.ItemHolidayBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.time.LocalDate
import java.time.Year
import java.util.UUID

// ======================= MODELS =======================

/** One document in the Firestore "events" collection. */
data class Event(
    @DocumentId val id: String = "",
    val title: String = "",
    val date: String = "",
    val location: String = "",
    val description: String = "",
    val imageUrl: String = ""      // download URL from Firebase Storage
)

/** One item returned by the REST API. */
data class Holiday(
    val date: String,
    val localName: String,
    val name: String,
    val countryCode: String
)

// ======================= REST API =======================

interface HolidayApi {
    @GET("PublicHolidays/{year}/{country}")
    suspend fun getHolidays(@Path("year") year: Int, @Path("country") country: String): List<Holiday>
}

val holidayApi: HolidayApi by lazy {
    Retrofit.Builder()
        .baseUrl("https://date.nager.at/api/v3/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(HolidayApi::class.java)
}

// ======================= VALIDATION =======================

data class FormErrors(
    val title: String? = null,
    val date: String? = null,
    val location: String? = null,
    val description: String? = null
) {
    val hasErrors get() = listOf(title, date, location, description).any { it != null }
}

fun validateEvent(title: String, date: String, location: String, description: String): FormErrors {
    val parsed = runCatching { LocalDate.parse(date.trim()) }
    return FormErrors(
        title = when {
            title.isBlank() -> "Title is required"
            title.trim().length < 3 -> "Title must be at least 3 characters"
            else -> null
        },
        date = when {
            date.isBlank() -> "Date is required"
            parsed.isFailure -> "Use the format YYYY-MM-DD, e.g. 2026-12-25"
            else -> null
        },
        location = if (location.isBlank()) "Location is required" else null,
        description = when {
            description.isBlank() -> "Description is required"
            description.trim().length < 10 -> "Please write at least 10 characters"
            else -> null
        }
    )
}

// ======================= RECYCLERVIEW ADAPTERS =======================

class EventAdapter(private val onClick: (Event) -> Unit) :
    ListAdapter<Event, EventAdapter.VH>(Diff) {

    class VH(val b: ItemEventBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemEventBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = getItem(position)
        holder.b.tvTitle.text = e.title
        holder.b.tvDate.text = e.date
        holder.b.tvLocation.text = e.location
        if (e.imageUrl.isNotBlank()) {
            holder.b.ivEvent.load(e.imageUrl) { crossfade(true) }
        } else {
            holder.b.ivEvent.setImageResource(android.R.drawable.ic_menu_gallery)
        }
        holder.b.root.setOnClickListener { onClick(e) }
    }

    object Diff : DiffUtil.ItemCallback<Event>() {
        override fun areItemsTheSame(a: Event, b: Event) = a.id == b.id
        override fun areContentsTheSame(a: Event, b: Event) = a == b
    }
}

class HolidayAdapter(private val onUse: (Holiday) -> Unit) :
    ListAdapter<Holiday, HolidayAdapter.VH>(Diff) {

    class VH(val b: ItemHolidayBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemHolidayBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val h = getItem(position)
        holder.b.tvName.text = h.name
        holder.b.tvLocalAndDate.text = "${h.localName}  -  ${h.date}"
        holder.b.btnUse.setOnClickListener { onUse(h) }
    }

    object Diff : DiffUtil.ItemCallback<Holiday>() {
        override fun areItemsTheSame(a: Holiday, b: Holiday) = a.date == b.date && a.name == b.name
        override fun areContentsTheSame(a: Holiday, b: Holiday) = a == b
    }
}

// ======================= ACTIVITY =======================

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private var listener: ListenerRegistration? = null
    private var imageUri: Uri? = null

    private val eventAdapter = EventAdapter { showDetails(it) }
    private val holidayAdapter = HolidayAdapter { holiday ->
        b.etTitle.setText(holiday.name)
        b.etDate.setText(holiday.date)
        b.scroll.smoothScrollTo(0, 0)
    }

    private val imagePicker =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                imageUri = uri
                b.ivPreview.visibility = View.VISIBLE
                b.ivPreview.load(uri)
                b.btnPickImage.text = "Change image"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        b.tvUser.text = "Signed in as ${user.email}"
        b.btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(
                Intent(this, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
        }

        b.rvEvents.adapter = eventAdapter
        b.rvHolidays.adapter = holidayAdapter

        b.btnPickImage.setOnClickListener {
            imagePicker.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly
                )
            )
        }
        b.btnSave.setOnClickListener { saveEvent() }
        b.btnRetry.setOnClickListener { loadHolidays() }

        observeEvents()
        loadHolidays()
    }

    override fun onDestroy() {
        listener?.remove()
        super.onDestroy()
    }

    private fun observeEvents() {
        b.progressEvents.visibility = View.VISIBLE
        listener = db.collection("events").orderBy("date")
            .addSnapshotListener { snap, err ->
                b.progressEvents.visibility = View.GONE
                if (err != null) {
                    b.tvEventsMessage.visibility = View.VISIBLE
                    b.tvEventsMessage.text = "Could not load events: ${err.message}"
                    return@addSnapshotListener
                }
                val list = snap?.toObjects(Event::class.java).orEmpty()
                eventAdapter.submitList(list)
                b.tvEventsMessage.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                b.tvEventsMessage.text = "No events yet. Add one above."
            }
    }

    private fun loadHolidays() {
        b.progressHolidays.visibility = View.VISIBLE
        b.tvHolidaysMessage.visibility = View.GONE
        b.btnRetry.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val list = holidayApi.getHolidays(Year.now().value, "ZA")
                holidayAdapter.submitList(list)
                b.tvHolidayCount.text = "${list.size} holidays received"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                b.tvHolidaysMessage.text = "Could not reach the server. Check your internet connection."
                b.tvHolidaysMessage.visibility = View.VISIBLE
                b.btnRetry.visibility = View.VISIBLE
            } finally {
                b.progressHolidays.visibility = View.GONE
            }
        }
    }

    private fun saveEvent() {
        b.tvSaveError.visibility = View.GONE

        val title = b.etTitle.text.toString()
        val date = b.etDate.text.toString()
        val location = b.etLocation.text.toString()
        val description = b.etDescription.text.toString()

        val errors = validateEvent(title, date, location, description)
        b.tilTitle.error = errors.title
        b.tilDate.error = errors.date
        b.tilLocation.error = errors.location
        b.tilDescription.error = errors.description
        if (errors.hasErrors) return

        b.btnSave.isEnabled = false
        b.btnSave.text = "Saving..."

        lifecycleScope.launch {
            try {
                var url = ""
                val uri = imageUri
                if (uri != null) {
                    val ref = storage.reference.child("event_images/${UUID.randomUUID()}.jpg")
                    ref.putFile(uri).await()
                    url = ref.downloadUrl.await().toString()
                }
                db.collection("events").add(
                    hashMapOf(
                        "title" to title.trim(),
                        "date" to date.trim(),
                        "location" to location.trim(),
                        "description" to description.trim(),
                        "imageUrl" to url
                    )
                ).await()

                clearForm()
                Snackbar.make(b.root, "Event saved!", Snackbar.LENGTH_SHORT).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                b.tvSaveError.text = "Could not save the event: ${e.message ?: "unknown error"}"
                b.tvSaveError.visibility = View.VISIBLE
            } finally {
                b.btnSave.isEnabled = true
                b.btnSave.text = "Save event"
            }
        }
    }

    private fun clearForm() {
        b.etTitle.text?.clear()
        b.etDate.text?.clear()
        b.etLocation.text?.clear()
        b.etDescription.text?.clear()
        imageUri = null
        b.ivPreview.visibility = View.GONE
        b.btnPickImage.text = "Choose event image"
    }

    private fun showDetails(e: Event) {
        val d = DialogEventDetailsBinding.inflate(layoutInflater)
        if (e.imageUrl.isNotBlank()) {
            d.ivImage.visibility = View.VISIBLE
            d.ivImage.load(e.imageUrl)
        }
        d.tvDate.text = "Date: ${e.date}"
        d.tvLocation.text = "Location: ${e.location}"
        d.tvDescription.text = e.description

        MaterialAlertDialogBuilder(this)
            .setTitle(e.title)
            .setView(d.root)
            .setPositiveButton("Close", null)
            .show()
    }
}
