package io.ipoli.android.home

import android.content.Intent
import android.view.*
import com.amplitude.api.Amplitude
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.FadeChangeHandler
import io.ipoli.android.MainActivity
import io.ipoli.android.R
import io.ipoli.android.common.view.FeedbackDialogController
import io.ipoli.android.quest.calendar.CalendarViewController
import kotlinx.android.synthetic.main.controller_home.view.*
import org.json.JSONObject
import android.content.Intent.ACTION_VIEW
import android.net.Uri
import android.widget.Toast
import io.ipoli.android.Constants


/**
 * Created by Venelin Valkov <venelin@ipoli.io>
 * on 8/19/17.
 */
class HomeController : Controller() {

//    private var navigationItemSelected: MenuItem? = null

//    override fun onNavigationItemSelected(item: MenuItem): Boolean {
//        navigationItemSelected = item
//        view?.drawerLayout?.closeDrawer(GravityCompat.START)
//        return false
//    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup): View {
        setHasOptionsMenu(true)
        return inflater.inflate(R.layout.controller_home, container, false)
    }

    override fun onAttach(view: View) {
        super.onAttach(view)

        val activity = activity as MainActivity
        activity.setSupportActionBar(view.toolbar)

        val actionBar = activity.supportActionBar
//        actionBar?.setDisplayHomeAsUpEnabled(true)

//        view.navigationView.setNavigationItemSelectedListener(this)

//        val actionBarDrawerToggle = object : ActionBarDrawerToggle(activity, view.drawerLayout, view.toolbar, R.string.drawer_open, R.string.drawer_close) {

//            override fun onDrawerOpened(drawerView: View) {
//                navigationItemSelected = null
//            }
//
//            override fun onDrawerClosed(drawerView: View) {
//                if (navigationItemSelected == null) {
//                    return
//                }
//                onItemSelectedFromDrawer()
//            }
//        }
//
//        view.drawerLayout.addDrawerListener(actionBarDrawerToggle)

        val handler = FadeChangeHandler()
        val childRouter = getChildRouter(view.controllerContainer, null)
        childRouter.setRoot(
            RouterTransaction.with(CalendarViewController())
                .pushChangeHandler(handler)
                .popChangeHandler(handler)
        )

//        actionBarDrawerToggle.syncState()

    }

//    private fun onItemSelectedFromDrawer() {
//        when (navigationItemSelected?.itemId) {
//            R.id.store -> Navigator(router).showStore()
//        }

//    }

//    override fun onOptionsItemSelected(item: MenuItem): Boolean {
//        if (item.itemId == android.R.id.home) {
//            view?.drawerLayout?.openDrawer(GravityCompat.START)
//            return true
//        }
//        return super.onOptionsItemSelected(item)
//    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.home_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_feedback) {
            FeedbackDialogController(object : FeedbackDialogController.FeedbackListener {
                override fun onSendFeedback(feedback: String) {
                    if(feedback.isNotEmpty()) {
                        Amplitude.getInstance().logEvent("feedback",
                            JSONObject().put("feedback", feedback))
                        Toast.makeText(activity!!, "Thank you!", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onChatWithUs() {
                    Amplitude.getInstance().logEvent("feedback_chat")
                    val myIntent = Intent(ACTION_VIEW, Uri.parse(Constants.DISCORD_CHAT_LINK))
                    startActivity(myIntent)
                }


            }).showDialog(router, "feedback-dialog")
            return true
        }
        return super.onOptionsItemSelected(item)
    }

}