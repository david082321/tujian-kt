package com.yarolegovich.slidingrootnav.transform

import android.view.*
import com.yarolegovich.slidingrootnav.util.*


class ElevationTransformation(private val endElevation: Float) : RootTransformation {
  override fun transform(dragProgress: Float, rootView: View) {
    rootView.elevation = slideEvaluate(dragProgress, START_ELEVATION, endElevation)
  }

  companion object {
    private const val START_ELEVATION = 0f
  }
}