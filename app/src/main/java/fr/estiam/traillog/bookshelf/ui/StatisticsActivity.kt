package fr.estiam.traillog.bookshelf.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import fr.estiam.traillog.R
import fr.estiam.traillog.bookshelf.BookshelfApplication
import fr.estiam.traillog.bookshelf.domain.CategoryStat
import fr.estiam.traillog.bookshelf.domain.StatisticsSummary
import fr.estiam.traillog.databinding.ActivityStatisticsBinding
import fr.estiam.traillog.databinding.ItemCategoryStatBinding
import kotlinx.coroutines.launch

/** Reading statistics: totals, average speed, reading streak and per-category breakdown. */
class StatisticsActivity : AppCompatActivity() {
    private val app get() = application as BookshelfApplication
    private val vm by viewModels<StatisticsViewModel> { StatisticsViewModel.Factory(app.repository) }
    private lateinit var binding: ActivityStatisticsBinding
    private val adapter = CategoryStatAdapter()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        binding = ActivityStatisticsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.categoryStatsList.layoutManager = LinearLayoutManager(this)
        binding.categoryStatsList.adapter = adapter
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { vm.state.collect(::render) }
        }
    }

    private fun render(state: StatisticsViewModel.UiState) {
        val summary = (state as? StatisticsViewModel.UiState.Ready)?.summary
        binding.statsLoading.visibility = if (summary == null) View.VISIBLE else View.GONE
        binding.statsEmpty.visibility = if (summary?.isEmpty == true) View.VISIBLE else View.GONE
        binding.statsContent.visibility = if (summary != null && !summary.isEmpty) View.VISIBLE else View.GONE
        if (summary != null && !summary.isEmpty) renderSummary(summary)
    }

    private fun renderSummary(s: StatisticsSummary) {
        fun line(view: TextView, label: Int, value: String) { view.text = "${getString(label)} : $value" }
        line(binding.statsCompleted, R.string.stats_completed, s.completedBooks.toString())
        line(binding.statsFavorites, R.string.stats_favorites, s.favoriteBooks.toString())
        line(binding.statsPages, R.string.stats_pages, s.pagesRead.toString())
        line(binding.statsMinutes, R.string.stats_minutes, BookDetailActivity.formatDuration(this, s.totalMinutes))
        line(binding.statsSpeed, R.string.stats_speed,
            s.averagePagesPerHour?.let { getString(R.string.stats_speed_value, it) } ?: getString(R.string.stats_speed_unknown))
        line(binding.statsStreak, R.string.stats_streak, getString(R.string.stats_streak_value, s.streakDays))
        adapter.submitList(s.categories)
    }
}

/** One row per category: name, number of books, pages read and minutes. */
private class CategoryStatAdapter : ListAdapter<CategoryStat, CategoryStatAdapter.Holder>(DIFF) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemCategoryStatBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val stat = getItem(position)
        holder.binding.categoryName.text = stat.categoryName
        holder.binding.categoryDetails.text = holder.binding.root.context.getString(
            R.string.stats_category_details, stat.bookCount, stat.pagesRead, stat.minutes)
    }

    class Holder(val binding: ItemCategoryStatBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<CategoryStat>() {
            override fun areItemsTheSame(a: CategoryStat, b: CategoryStat) = a.categoryId == b.categoryId
            override fun areContentsTheSame(a: CategoryStat, b: CategoryStat) = a == b
        }
    }
}
