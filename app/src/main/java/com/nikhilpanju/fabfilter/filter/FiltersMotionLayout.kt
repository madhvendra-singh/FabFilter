package com.nikhilpanju.fabfilter.filter

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import androidx.cardview.widget.CardView
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.nikhilpanju.fabfilter.R
import com.nikhilpanju.fabfilter.main.MainActivity
import com.nikhilpanju.fabfilter.utils.*
import kotlinx.coroutines.launch


@SuppressLint("WrongConstant")
class FiltersMotionLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : MultiListenerMotionLayout(context, attrs, defStyleAttr) {

    private val fab: View by bindView(R.id.fab)
    private val tabsRecyclerView: NoScrollRecyclerView by bindView(R.id.tabs_recycler_view)
    private val viewPager: ViewPager2 by bindView(R.id.view_pager)
    private val closeIcon: ImageView by bindView(R.id.close_icon)
    private val bottomBarCardView: CardView by bindView(R.id.bottom_bar_card_view)

    ///////////////////////////////////////////////////////////////////////////
    // Colors & Dimens
    ///////////////////////////////////////////////////////////////////////////

    private val bottomBarColor: Int by bindColor(R.color.bottom_bar_color)
    private val bottomBarPinkColor: Int by bindColor(R.color.colorAccent)
    private val tabColor: Int by bindColor(R.color.tab_unselected_color)
    private val tabSelectedColor: Int by bindColor(R.color.tab_selected_color)

    private val tabItemWidth: Float by bindDimen(R.dimen.tab_item_width)
    private val filterLayoutPadding: Float by bindDimen(R.dimen.filter_layout_padding)

    private lateinit var tabsAdapter: FiltersTabsAdapter
    private var totalTabsScroll = 0
    private var hasActiveFilters = false

    init {
        inflate(context, R.layout.layout_filter_motion, this)

        fab.setOnClickListener {
            //            transitionToState(R.id.path_set)
            (context as MainActivity).lifecycleScope.launch {
                setAdapters(true)
                transitionToState(R.id.path_set)
                awaitTransitionComplete(R.id.path_set)

                transitionToState(R.id.reveal_set)
                awaitTransitionComplete(R.id.reveal_set)

                transitionToState(R.id.settle_set)
            }
        }
        closeIcon.setOnClickListener {
            (context as MainActivity).lifecycleScope.launch {
                transitionToState(R.id.collapse_set)
                awaitTransitionComplete(R.id.original_filtered_set)
                setAdapters(false)
            }
//                setTransition(R.id.settle_transition)
//                transitionToStart()
//                awaitTransitionComplete(R.id.reveal_set)
//
//                setTransition(R.id.reveal_transition)
//                transitionToStart()
//                awaitTransitionComplete(R.id.path_set)
//
//                setTransition(R.id.path_transition)
//                transitionToStart()
//            }
        }

        // ViewPager & Tabs
        viewPager.offscreenPageLimit = FiltersLayout.numTabs
        tabsRecyclerView.updatePadding(right = (screenWidth - tabItemWidth - filterLayoutPadding).toInt())
        tabsRecyclerView.layoutManager = NoScrollHorizontalLayoutManager(context)

        // Sync Tabs And Pager
        tabsRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                totalTabsScroll += dx
            }
        })

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                // Scroll tabs as viewpager is scrolled
                val dx = (position + positionOffset) * tabItemWidth - totalTabsScroll
                tabsRecyclerView.scrollBy(dx.toInt(), 0)

                // This acts like a page transformer for tabsRecyclerView. Ideally we should do this in the
                // onScrollListener for the RecyclerView but that requires extra math. positionOffset
                // is all we need so let's use that to apply transformation to the tabs

                val currentTabView = tabsRecyclerView.layoutManager?.findViewByPosition(position)!!
                val nextTabView = tabsRecyclerView.layoutManager?.findViewByPosition(position + 1)

                val defaultScale: Float = FiltersTabsAdapter.defaultScale
                val maxScale: Float = FiltersTabsAdapter.maxScale

                currentTabView.setScale(defaultScale + (1 - positionOffset) * (maxScale - defaultScale))
                nextTabView?.setScale(defaultScale + positionOffset * (maxScale - defaultScale))

                currentTabView.findViewById<View>(R.id.tab_pill).backgroundTintList =
                        ColorStateList.valueOf(blendColors(tabColor, tabSelectedColor, 1 - positionOffset))
                nextTabView?.findViewById<View>(R.id.tab_pill)?.backgroundTintList =
                        ColorStateList.valueOf(blendColors(tabColor, tabSelectedColor, positionOffset))
            }
        })
    }

    /**
     * Used to set tab and view pager adapters and remove them when unnecessary.
     * This is done because keeping the adapters around when fab is never clicked is wasteful.
     */
    private fun setAdapters(set: Boolean) {
        if (set) {
            viewPager.adapter = FiltersPagerAdapter(context!!, ::onFilterSelected)

            // Tabs
            tabsAdapter = FiltersTabsAdapter(context!!) { clickedPosition ->
                // smoothScroll = true will call the onPageScrolled callback which will smoothly
                // animate (transform) the tabs accordingly
                viewPager.setCurrentItem(clickedPosition, true)
            }
            tabsRecyclerView.adapter = tabsAdapter
        } else {
            viewPager.adapter = null
            tabsRecyclerView.adapter = null
            hasActiveFilters = false
            totalTabsScroll = 0
        }
    }

    /**
     * Callback method for FiltersPagerAdapter. When ever a filter is selected, adapter will call this function.
     * Animates the bottom bar to pink if there are any active filters and vice versa
     */
    private fun onFilterSelected(updatedPosition: Int, selectedMap: Map<Int, List<Int>>) {
        val hasActiveFilters = selectedMap.filterValues { it.isNotEmpty() }.isNotEmpty()
        val bottomBarAnimator =
                if (hasActiveFilters && !this.hasActiveFilters) ValueAnimator.ofFloat(0f, 1f)
                else if (!hasActiveFilters && this.hasActiveFilters) ValueAnimator.ofFloat(1f, 0f)
                else null

        tabsAdapter.updateBadge(updatedPosition, !selectedMap[updatedPosition].isNullOrEmpty())

        bottomBarAnimator?.let {
            this.hasActiveFilters = !this.hasActiveFilters
            it.addUpdateListener { animation ->
                val color = blendColors(bottomBarColor, bottomBarPinkColor, animation.animatedValue as Float)
                bottomBarCardView.setCardBackgroundColor(color)
            }
            it.duration = FiltersLayout.toggleDuration
            it.start()
        }
    }
}