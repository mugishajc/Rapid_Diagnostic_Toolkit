package org.rdtoolkit.ui.sessions

import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.common.util.concurrent.ListenableFuture
import org.rdtoolkit.R
import org.rdtoolkit.processing.ImageSubmissionWorker.Companion.TAG_MEDIA
import org.rdtoolkit.processing.SessionPurgeWorker.Companion.TAG_PURGE
import org.rdtoolkit.processing.SessionSubmissionWorker.Companion.TAG_SESSION
import org.rdtoolkit.processing.WorkCoordinator
import org.rdtoolkit.support.model.session.STATUS
import org.rdtoolkit.support.model.session.TestReadableState
import org.rdtoolkit.support.model.session.TestSession
import org.rdtoolkit.ui.sessions.TestSessionsAdapter.TestSessionViewHolder


class TestSessionsAdapter(private val sessionsViewModel : SessionsViewModel
) : ListAdapter<TestSession, TestSessionViewHolder>(DIFF_CALLBACK) {

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder.
    // Each data item is just a string in this case that is shown in a TextView.
    class TestSessionViewHolder(val view : View) : RecyclerView.ViewHolder(view)


    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(parent: ViewGroup,
                                    viewType: Int): TestSessionViewHolder {
        // create a new view
        val frame = LayoutInflater.from(parent.context)
                .inflate(R.layout.card_view_session, parent, false) as View
        return TestSessionViewHolder(frame)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(holder: TestSessionViewHolder, position: Int) {
        val context = holder.view.context;

        var session = this.getItem(position)

        holder.view.findViewById<TextView>(R.id.sessions_card_title).text =
                String.format(context.getString(R.string.sessions_card_title_text),
                        sessionsViewModel.getDiagnosticsRepo().getTestProfile(session.testProfileId)
                                .readableName())

        //For debugging
        val statuses = WorkManager.getInstance(context).getWorkInfosByTag(session.sessionId)

        val workInfoList = statuses.get()
        var statusTag = ""
        for (info in workInfoList) {
            var tag = "U"
            if (info.tags.contains(TAG_PURGE)) {
                tag = "P"
            } else if (info.tags.contains(TAG_MEDIA)) {
                tag = "M"
            } else if (info.tags.contains(TAG_SESSION)) {
                tag = "S"
            }

            tag = "$tag.${info.state.toString()[0]}"
            statusTag = "${statusTag} ${tag}"
        }

        val statusText = holder.view.findViewById<TextView>(R.id.sessions_card_actions)
        statusText.visibility = GONE
        statusText.text = statusTag
        statusText.setOnClickListener {
            WorkCoordinator(context).processTestSession(session, true)
        }


        holder.view.findViewById<TextView>(R.id.sessions_card_text_flavor_one).text =
                session.configuration.flavorText
        holder.view.findViewById<TextView>(R.id.sessions_card_text_flavor_two).text =
                session.configuration.flavorTextTwo

        var captureButton = holder.view.findViewById<TextView>(R.id.sessions_card_button_capture)

        captureButton.tag = session.sessionId
        if (session.state == STATUS.RUNNING && (session.getTestReadableState() == TestReadableState.READABLE || session.getTestReadableState() == TestReadableState.RESOLVING)) {
            captureButton.visibility = View.VISIBLE
        } else {
            captureButton.visibility = View.GONE
        }
    }
}
var DIFF_CALLBACK = object: DiffUtil.ItemCallback<TestSession>() {
    override fun areItemsTheSame(oldItem: TestSession, newItem: TestSession): Boolean {
        return oldItem.sessionId == newItem.sessionId
    }

    override fun areContentsTheSame(oldItem: TestSession, newItem: TestSession): Boolean {
        return oldItem.equals(newItem)
    }
}


