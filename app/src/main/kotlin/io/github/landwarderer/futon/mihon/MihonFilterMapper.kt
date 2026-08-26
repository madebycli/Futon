package io.github.landwarderer.futon.mihon

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import io.github.landwarderer.futon.mihon.parsers.InternalParsersApi
import io.github.landwarderer.futon.mihon.parsers.model.ContentListFilter
import io.github.landwarderer.futon.mihon.parsers.model.ContentListFilterOptions
import io.github.landwarderer.futon.mihon.parsers.model.ContentSource
import io.github.landwarderer.futon.mihon.parsers.model.ContentTag
import io.github.landwarderer.futon.mihon.parsers.model.ContentTagGroup
import io.github.landwarderer.futon.mihon.parsers.util.mapToSet

/**
 * Maps Tachiyomi/Mihon filter objects to Futon's generic filter model.
 *
 * Complex extensions sometimes expose data-class values such as
 * `ThemeInfo(name=爱情, pathWord=xiaoyuan)` rather than plain strings. Kototoro normalizes those
 * values before exposing them to the UI and before matching a saved selection back to the Mihon
 * filter. Keeping both directions symmetrical avoids displaying implementation details and, more
 * importantly, avoids creating filter keys that can never be selected again.
 */
@OptIn(InternalParsersApi::class)
object MihonFilterMapper {

    private const val PREFIX_TOP = "top:"
    private const val PREFIX_SORT = "sort:"
    private const val PREFIX_TEXT = "text:"

    fun mapOptions(mihonFilters: FilterList, source: ContentSource): ContentListFilterOptions {
        val tagGroups = mutableListOf<ContentTagGroup>()
        var currentHeader = "General"

        mihonFilters.forEach { filter ->
            when (filter) {
                is Filter.Header -> {
                    currentHeader = filter.name
                }

                is Filter.Separator -> Unit

                is Filter.Group<*> -> {
                    val state = filter.state
                    if (state is List<*>) {
                        val checkboxTags = mutableListOf<ContentTag>()
                        state.forEach { subItem ->
                            if (subItem is Filter<*>) {
                                when (subItem) {
                                    is Filter.Select<*> -> {
                                        val selectTags = mapFilterToTags(subItem, filter.name, source)
                                        if (selectTags.isNotEmpty()) {
                                            tagGroups.add(
                                                ContentTagGroup(
                                                    "${filter.name} - ${subItem.name}",
                                                    selectTags.toSet(),
                                                ),
                                            )
                                        }
                                    }

                                    is Filter.Sort -> {
                                        val sortTags = mapFilterToTags(subItem, filter.name, source)
                                        if (sortTags.isNotEmpty()) {
                                            tagGroups.add(
                                                ContentTagGroup(
                                                    "${filter.name} - ${subItem.name}",
                                                    sortTags.toSet(),
                                                ),
                                            )
                                        }
                                    }

                                    is Filter.Group<*> -> {
                                        checkboxTags.addAll(mapFilterToTags(subItem, filter.name, source))
                                    }

                                    else -> {
                                        checkboxTags.addAll(mapFilterToTags(subItem, filter.name, source))
                                    }
                                }
                            }
                        }
                        if (checkboxTags.isNotEmpty()) {
                            tagGroups.add(ContentTagGroup(filter.name, checkboxTags.toSet()))
                        }
                    }
                }

                else -> {
                    val tags = mapFilterToTags(filter, null, source)
                    if (tags.isNotEmpty()) {
                        tagGroups.add(ContentTagGroup(currentHeader, tags.toSet()))
                    }
                }
            }
        }

        val mergedGroups = tagGroups.groupBy { it.title }.map { (title, groups) ->
            ContentTagGroup(title, groups.flatMap { it.tags }.toSet())
        }

        return ContentListFilterOptions(
            availableTags = mergedGroups.flatMap { it.tags }.toSet(),
            tagGroups = mergedGroups,
        )
    }

    private fun mapFilterToTags(
        filter: Filter<*>,
        parentName: String?,
        source: ContentSource,
    ): List<ContentTag> {
        val prefix = if (parentName != null) "$parentName/" else PREFIX_TOP

        return when (filter) {
            is Filter.CheckBox -> {
                listOf(ContentTag(filter.name.cleanTitle(), "$prefix${filter.name}", source))
            }

            is Filter.TriState -> {
                listOf(ContentTag(filter.name.cleanTitle(), "$prefix${filter.name}", source))
            }

            is Filter.Select<*> -> {
                filter.values.mapNotNull { value ->
                    val title = value.cleanTitle()
                    if (title.isBlank()) {
                        null
                    } else {
                        ContentTag(title, "$prefix${filter.name}/$title", source)
                    }
                }
            }

            is Filter.Sort -> {
                filter.values.map { value ->
                    ContentTag(value, "$PREFIX_SORT$prefix${filter.name}/$value", source)
                }
            }

            is Filter.Text -> {
                listOf(
                    ContentTag(
                        title = "📝 ${filter.name}",
                        key = "$PREFIX_TEXT$prefix${filter.name}",
                        source = source,
                    ),
                )
            }

            is Filter.Group<*> -> {
                val nestedTags = mutableListOf<ContentTag>()
                (filter.state as? List<*>)?.forEach { subItem ->
                    if (subItem is Filter<*>) {
                        val nestedPrefix = if (parentName != null) {
                            "$parentName/${filter.name}"
                        } else {
                            filter.name
                        }
                        nestedTags.addAll(mapFilterToTags(subItem, nestedPrefix, source))
                    }
                }
                nestedTags
            }

            else -> emptyList()
        }
    }

    fun updateMihonFilters(mihonFilters: FilterList, contentListFilter: ContentListFilter) {
        val selectedTags = contentListFilter.tags.mapToSet { it.key }
        val excludedTags = contentListFilter.tagsExclude.mapToSet { it.key }

        mihonFilters.forEach { filter ->
            when (filter) {
                is Filter.Group<*> -> {
                    (filter.state as? List<*>)?.forEach { subItem ->
                        val sub = subItem as? Filter<*> ?: return@forEach
                        updateSingleFilter(sub, filter.name, selectedTags, excludedTags)
                    }
                }

                else -> updateSingleFilter(filter, null, selectedTags, excludedTags)
            }
        }
    }

    private fun updateSingleFilter(
        filter: Filter<*>,
        parentName: String?,
        selectedTags: Set<String>,
        excludedTags: Set<String>,
    ) {
        val prefix = if (parentName != null) "$parentName/" else PREFIX_TOP
        when (filter) {
            is Filter.CheckBox -> {
                val key = "$prefix${filter.name}"
                filter.state = key in selectedTags
            }

            is Filter.TriState -> {
                val key = "$prefix${filter.name}"
                filter.state = when {
                    key in selectedTags -> Filter.TriState.STATE_INCLUDE
                    key in excludedTags -> Filter.TriState.STATE_EXCLUDE
                    else -> Filter.TriState.STATE_IGNORE
                }
            }

            is Filter.Select<*> -> {
                filter.values.forEachIndexed { index, value ->
                    val key = "$prefix${filter.name}/${value.cleanTitle()}"
                    if (key in selectedTags) {
                        filter.state = index
                    }
                }
            }

            is Filter.Sort -> {
                filter.values.forEachIndexed { index, value ->
                    val key = "$PREFIX_SORT$prefix${filter.name}/$value"
                    if (key in selectedTags) {
                        filter.state = Filter.Sort.Selection(index, filter.state?.ascending ?: false)
                    }
                }
            }

            is Filter.Text -> {
                val baseKey = "$PREFIX_TEXT$prefix${filter.name}"
                selectedTags.find { it.startsWith(baseKey) }?.let { matchingTag ->
                    filter.state = if (matchingTag.contains("=")) {
                        matchingTag.substringAfter("=")
                    } else {
                        ""
                    }
                }
            }

            is Filter.Group<*> -> {
                (filter.state as? List<*>)?.forEach { subItem ->
                    if (subItem is Filter<*>) {
                        val nestedPrefix = if (parentName != null) {
                            "$parentName/${filter.name}"
                        } else {
                            filter.name
                        }
                        updateSingleFilter(subItem, nestedPrefix, selectedTags, excludedTags)
                    }
                }
            }

            is Filter.Header,
            is Filter.Separator,
            -> Unit

            else -> Unit
        }
    }

    /**
     * Extract a stable human-readable label from extension-defined filter values.
     *
     * Examples:
     * `ThemeInfo(name=爱情, pathWord=xiaoyuan)` becomes `爱情`.
     * A broken fragment such as `pathWord=aiqing)` is ignored instead of becoming a tag.
     */
    private fun Any?.cleanTitle(): String {
        if (this == null) return ""
        val raw = toString()
        val classPattern = Regex("""^\w+\((\w+)=([^,)]+)""")
        classPattern.find(raw)?.let { match ->
            return match.groupValues[2]
        }
        if (raw.matches(Regex("""^\w+=[^,)]+\)?$"""))) {
            return ""
        }
        return raw
    }
}
