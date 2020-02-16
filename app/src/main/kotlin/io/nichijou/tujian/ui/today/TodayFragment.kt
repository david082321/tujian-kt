package io.nichijou.tujian.ui.today

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.ArrayAdapter
import android.widget.ListPopupWindow
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.palette.graphics.Palette
import androidx.viewpager2.widget.ViewPager2
import com.facebook.common.executors.UiThreadImmediateExecutorService
import com.facebook.common.references.CloseableReference
import com.facebook.datasource.DataSource
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.common.ImageDecodeOptions
import com.facebook.imagepipeline.common.Priority
import com.facebook.imagepipeline.common.RotationOptions
import com.facebook.imagepipeline.datasource.BaseBitmapDataSubscriber
import com.facebook.imagepipeline.image.CloseableImage
import com.facebook.imagepipeline.request.ImageRequest
import com.facebook.imagepipeline.request.ImageRequestBuilder
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.material.tabs.TabLayoutMediator
import com.zzhoujay.richtext.RichText
import io.nichijou.tujian.R
import io.nichijou.tujian.base.BaseFragment
import io.nichijou.tujian.common.entity.Picture
import io.nichijou.tujian.common.ext.*
import io.nichijou.tujian.common.fresco.getPalette
import io.nichijou.tujian.ext.target
import io.nichijou.tujian.func.wallpaper.WallpaperConfig
import io.nichijou.tujian.getThemeColor
import io.nichijou.tujian.ui.ColorAdapter
import io.nichijou.tujian.ui.MainViewModel
import jp.wasabeef.fresco.processors.BlurPostprocessor
import jp.wasabeef.fresco.processors.CombinePostProcessors
import jp.wasabeef.fresco.processors.gpu.PixelationFilterPostprocessor
import jp.wasabeef.recyclerview.animators.LandingAnimator
import kotlinx.android.synthetic.main.fragment_today.*
import kotlinx.android.synthetic.main.view_today_item.*
import kotlinx.coroutines.launch
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.support.v4.toast
import org.jetbrains.anko.uiThread
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.*
import kotlin.collections.set

class TodayFragment : BaseFragment() {

  private val viewModel by viewModel<TodayViewModel>()

  override fun getFragmentViewId(): Int = R.layout.fragment_today

  override fun handleOnViewCreated() {
    initView()
    initViewModel()
  }

  private fun initViewModel() {
    viewModel.getToday().observe(this, Observer(::bind2View))
    viewModel.msg.observe(this, Observer {
      toast(it)
    })
  }

  private var currentPicture: Picture? = null

  private fun bind2View(pictures: List<Picture>) {
    if (pictures.isEmpty()) {
      toast(R.string.no_data_available)
      return
    }
    if (content == null) return
    content.makeVisible()
    fab.makeVisible()
    currentPicture = pictures[0]
    bindInfo()// bind once
    view_pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
      override fun onPageSelected(position: Int) {
        currentPicture = pictures[position]
        bindInfo()// bind twice
        palette()
      }
    })
    view_pager.adapter = TodayAdapter(pictures)
    TabLayoutMediator(tab, view_pager) { tab, position ->
      tab.text = pictures[position].category
    }.attach()
  }

  private fun bindInfo() {
    currentPicture?.let {
      toolbar.title = it.title
      RichText.fromMarkdown(it.desc.replace("\n","  \n")).linkFix { holder ->
        holder!!.color = getThemeColor()
        holder.isUnderLine = false
      }.into(desc)
      val dat = if (it.from == Picture.FROM_BING) it.date else it.date.substring(5)
      val user = " via ${it.user}"
      val result = dat + user
      val string = SpannableString(result)
      string.setSpan(RelativeSizeSpan(0.7f), dat.length, result.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
      date.text = string
      name_tag.makeVisible()
      if (it.from != Picture.FROM_BING) {
        when (it.user) {
          "Galentwww", "Chimon89", "Createlite", "Night" -> name_tag.text = "运营"
          else -> name_tag.makeGone()
        }
      } else {
        name_tag.makeGone()
      }
//      name_tag.background.tint(Color.parseColor("#123456"))
    }
  }

  private val colorCaches by lazy(LazyThreadSafetyMode.NONE) { hashMapOf<String, Palette>() }

  private fun palette() {
    currentPicture?.local?.let { url ->
      val palette = colorCaches[url]
      if (palette == null) {
        ImageRequest.fromUri(url)?.getPalette {
          if (it != null && it.swatches.size > 2) {
            applyPalette(it)
            colorCaches[url] = it
          }
        }
      } else {
        applyPalette(palette)
      }
    }
  }

  private val mainViewModel by activityViewModels<MainViewModel>()
  private fun initView() {
    mainViewModel.barColor.postValue(Color.TRANSPARENT)
    mainViewModel.enableScreenSaver.postValue(true)
    fab.setOnClickListener {
      fabRotation()
      showPop(it)
    }
    setupDrawerWithToolbar(toolbar)
    recycler_view?.layoutManager = FlexboxLayoutManager(context, FlexDirection.ROW, FlexWrap.NOWRAP)
    view_pager.offscreenPageLimit = Int.MAX_VALUE
    val singleGestureDetector = GestureDetector(target(), object : GestureDetector.SimpleOnGestureListener() {
      override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
        switchPanel()
        return true
      }
    })
    content_overlay.setOnTouchListener { v, event ->
      if (event.action == MotionEvent.ACTION_UP) v.performClick()
      singleGestureDetector.onTouchEvent(event)
      view_pager.dispatchTouchEvent(event)
      true
    }
  }

  private fun applyPalette(palette: Palette) {
    recycler_view?.apply {
      makeVisible()
      adapter = ColorAdapter(palette.swatches, -1, target().dp2px(8f).toInt())
      itemAnimator = LandingAnimator()
    }
  }

  private var isHidePanel = false
  private val dp32 by lazy(LazyThreadSafetyMode.NONE) { target().dp2px(32f) }

  //今日图片信息面板
  private fun switchPanel() {
    if (isHidePanel) {
      isHidePanel = false
      content.animate().alpha(1f).translationY(0f).setDuration(480).setInterpolator(OvershootInterpolator()).start()
      fab.animate().scaleX(1f).scaleY(1f).setDuration(400).start()
    } else {
      isHidePanel = true
      content.animate().alpha(0f).translationY(content.height.toFloat() + dp32).setDuration(480).start()
      fab.animate().scaleX(0f).scaleY(0f).setDuration(400).start()
    }
  }

  private var fabAnimated = false

  private fun fabOriginal() {
    if (fabAnimated) {
      fabAnimated = false
      fab.animate().rotation(0f).setDuration(360).start()
    }
  }

  private fun fabRotation() {
    if (!fabAnimated) {
      fabAnimated = true
      fab.animate().rotation(225f).setDuration(360).start()
    }
  }

  //fab点击弹出来的
  private fun showPop(view: View) {
    val menu = arrayOf(getString(R.string.share), getString(R.string.download), getString(R.string.set_wallpaper))
    val adapter = ArrayAdapter(target(), android.R.layout.simple_list_item_1, menu)
    val popWidth = adapter.measureContentWidth(target())
    ListPopupWindow(target()).apply {
      setAdapter(adapter)
      setContentWidth(popWidth)
      anchorView = view
      horizontalOffset = -popWidth + view.width / 2
      isModal = true
      setOnItemClickListener { _, _, pos, _ ->
        when (pos) {
          0 -> {
            target().shareString(currentPicture?.share())
          }
          1 -> {
            actual_view.saveView(context!!, currentPicture?.title + Date())
//            currentPicture?.download(target())
          }
          2 -> {
            setWallpaper()
          }
        }
        dismiss()
      }
      setOnDismissListener {
        fabOriginal()
      }
      show()
    }
  }

  private fun setWallpaper() = lifecycleScope.launch {
    val local = currentPicture?.local ?: return@launch
    val uri = Uri.parse(local) ?: return@launch
    val builder = ImageRequestBuilder.newBuilderWithSource(uri)
      .setRotationOptions(RotationOptions.autoRotate())
      .setRequestPriority(Priority.HIGH)
      .setImageDecodeOptions(ImageDecodeOptions.newBuilder().setBitmapConfig(Bitmap.Config.ARGB_8888).build())
    val blur = WallpaperConfig.blur
    val pixel = WallpaperConfig.pixel
    if (blur || pixel) {
      val processorBuilder = CombinePostProcessors.Builder()
      if (blur) processorBuilder.add(BlurPostprocessor(target(), WallpaperConfig.blurValue))
      if (pixel) processorBuilder.add(PixelationFilterPostprocessor(target(), WallpaperConfig.pixelValue.toFloat()))
      builder.postprocessor = processorBuilder.build()
    }
    toast(R.string.start_set_wallpaper)
    val imageRequest = builder.build()
    val dataSource = Fresco.getImagePipeline().fetchDecodedImage(imageRequest, null)
    dataSource.subscribe(object : BaseBitmapDataSubscriber() {
      override fun onFailureImpl(dataSource: DataSource<CloseableReference<CloseableImage>>) {
        toast(R.string.set_wallpaper_failure)
      }

      override fun onNewResultImpl(bitmap: Bitmap?) {
        if (bitmap != null) {
          doAsync {
            WallpaperManager.getInstance(target()).setBitmap(bitmap)
            uiThread {
              toast(R.string.set_wallpaper_success)
            }
          }
        }
      }
    }, UiThreadImmediateExecutorService.getInstance())
  }

  companion object {
    fun newInstance() = TodayFragment()
  }
}


