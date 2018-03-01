/*
 * Copyright (c) 2017 Zac Sweers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.sweers.catchup.service.github

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.Reusable
import dagger.multibindings.IntoMap
import io.reactivex.Maybe
import io.sweers.catchup.gemoji.EmojiMarkdownConverter
import io.sweers.catchup.service.api.*
import io.sweers.catchup.service.github.model.*
import io.sweers.catchup.service.github.type.LanguageOrder
import io.sweers.catchup.service.github.type.LanguageOrderField
import io.sweers.catchup.service.github.type.OrderDirection
import io.sweers.catchup.util.nullIfBlank
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.kotlinq.api.JsonParser
import org.threeten.bp.Instant
import javax.inject.Inject
import javax.inject.Qualifier

@Qualifier
private annotation class InternalApi

private const val SERVICE_KEY = "github"

internal class GitHubService @Inject constructor(
    @InternalApi private val serviceMeta: ServiceMeta,
    private val apolloClient: OkHttpClient,
    private val emojiMarkdownConverter: EmojiMarkdownConverter,
    private val linkHandler: LinkHandler)
  : TextService {

  override fun meta() = serviceMeta

  @Suppress("UNCHECKED_CAST")
  override fun fetchPage(request: DataRequest): Maybe<DataResult> {
    val query = SearchQuery(
        createdSince = TrendingTimespan.WEEK.createdSince(),
        minStars = 50)
        .toString()

    val alternateQuery = TrendingQuery.Builder()
        .order(LanguageOrder.builder()
            .direction(OrderDirection.DESC)
            .field(LanguageOrderField.SIZE)
            .build())
        .after(request.pageId.nullIfBlank())
        .build(SearchQuery(
            createdSince = TrendingTimespan.WEEK.createdSince(),
            minStars = 50).toString())

    return Maybe.just(
        Request.Builder()
            .post(RequestBody.create(MediaType.parse("application/graphql"),
                TrendingQuery.compatibleRequestString(alternateQuery.fragment)))
            .build()
            .let(apolloClient::newCall)
            .execute()
            .let {
              JsonParser.parseToObject(it.body()?.string() ?: "")
            }.let {
              PageInfo(it["pageInfo"] as Map<String, Any?>) to it["nodes"] as Iterable<Map<String, Any>>
            }.let { (info, maps) ->
              maps.map(::Repository)
                  .map {
                    with(it) {
                      CatchUpItem(
                          id = id.hashCode().toLong(),
                          hideComments = true,
                          title = "$name - $description",
                          score = "★" to stargazers,
                          timestamp = Instant.parse(createdAt),
                          author = owner,
                          tag = languages,
                          source = licenseInfo,
                          itemClickUrl = url)
                    }
                  }.let {
                    DataResult(it, info.endCursor)
                  }
            })
  }

  override fun linkHandler() = linkHandler
}

@Module
abstract class GitHubMetaModule {

  @IntoMap
  @ServiceMetaKey(SERVICE_KEY)
  @Binds
  internal abstract fun githubServiceMeta(@InternalApi meta: ServiceMeta): ServiceMeta

  @Module
  companion object {

    @InternalApi
    @Provides
    @Reusable
    @JvmStatic
    internal fun provideGitHubServiceMeta() = ServiceMeta(
        SERVICE_KEY,
        R.string.github,
        R.color.githubAccent,
        R.drawable.logo_github,
        firstPageKey = ""
    )
  }
}

@Module(includes = [GitHubMetaModule::class])
abstract class GitHubModule {

  @IntoMap
  @ServiceKey(SERVICE_KEY)
  @Binds
  internal abstract fun githubService(githubService: GitHubService): Service

}
