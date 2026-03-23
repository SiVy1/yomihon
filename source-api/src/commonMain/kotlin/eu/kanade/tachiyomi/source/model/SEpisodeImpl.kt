@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.source.model

class SEpisodeImpl : SEpisode {

    override lateinit var url: String

    override lateinit var name: String

    override var date_upload: Long = 0L

    override var episode_number: Float = -1f

    override var season_number: Int = -1

    override var release_group: String? = null
}
