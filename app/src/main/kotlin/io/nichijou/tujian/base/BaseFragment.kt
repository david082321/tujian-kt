package io.nichijou.tujian.base

import android.os.*
import android.view.*
import androidx.appcompat.app.*
import androidx.appcompat.widget.*
import androidx.fragment.app.*
import androidx.lifecycle.*
import com.yarolegovich.slidingrootnav.util.*
import io.nichijou.oops.ext.*
import io.nichijou.tujian.R
import io.nichijou.tujian.ext.*
import io.nichijou.tujian.ui.*

abstract class BaseFragment : Fragment(), FragmentBackHandler {
  protected abstract fun getFragmentViewId(): Int

  protected abstract fun handleOnViewCreated()

  protected open fun getMenuResId() = NO_MENU

  protected open fun onMenuItemSelected(item: MenuItem) {}
  protected open fun needClearMenu() = true

  override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
    if (needClearMenu()) menu.clear()
    if (getMenuResId() != NO_MENU) {
      inflater.inflate(getMenuResId(), menu)
    }
    super.onCreateOptionsMenu(menu, inflater)
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    onMenuItemSelected(item)
    return super.onOptionsItemSelected(item)
  }

  protected open fun interceptTouch() = true

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    val view = inflater.inflate(getFragmentViewId(), container, false)
    if (interceptTouch()) {
      view.setOnTouchListener { _, _ -> true }
    }
    handleOnCreateView(view)
    setHasOptionsMenu(true)
    return view
  }

  protected open fun handleOnCreateView(view: View) {
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    handleOnViewCreated()
  }

  protected fun close() {
    target().supportFragmentManager.popBackStack()
  }

  override fun onBackPressed(): Boolean {
    return interceptBackPressed() || handleBackPress()
  }

  open fun interceptBackPressed(): Boolean {
    return false
  }

  private lateinit var drawerToggleListenerAdapter: DrawerToggleListenerAdapter
  private lateinit var toggle: ActionBarDrawerToggle

  protected fun setupDrawerWithToolbar(toolbar: Toolbar) {
    val activity = target()
    activity.setSupportActionBar(toolbar)
    if (activity is MainActivity) {
      val toggleAdapter = ActionBarToggleAdapter(activity, activity.drawer.getDrawer())
      toggle = ActionBarDrawerToggle(activity, toggleAdapter, toolbar, R.string.srn_drawer_open, R.string.srn_drawer_close)
      drawerToggleListenerAdapter = DrawerToggleListenerAdapter(toggle, activity.drawer.getMenuView())
      activity.drawer.addDrawerListener(drawerToggleListenerAdapter)
      toggle.syncState()
      applyOopsThemeStore {
        colorAccent.observe(this@BaseFragment, Observer {
          toggle.drawerArrowDrawable.color = it
        })
      }
    }
  }

  protected fun setupBackWithToolbar(toolbar: Toolbar?) {
    target().setSupportActionBar(toolbar)
    toolbar?.setNavigationOnClickListener { close() }
  }

  protected fun setSupportActionBar(toolbar: Toolbar?) {
    target().setSupportActionBar(toolbar)
  }

  companion object {
    private const val NO_MENU = -1
  }

  override fun onDestroyView() {
    val activity = target()
    if (activity is MainActivity && ::drawerToggleListenerAdapter.isInitialized) {
      activity.drawer.removeDrawerListener(drawerToggleListenerAdapter)
    }
    super.onDestroyView()
  }
}