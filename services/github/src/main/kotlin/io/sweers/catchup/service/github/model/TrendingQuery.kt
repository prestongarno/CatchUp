package io.sweers.catchup.service.github.model

import io.sweers.catchup.service.github.type.LanguageOrder
import io.sweers.catchup.service.github.type.OrderDirection
import org.kotlinq.api.Fragment
import org.kotlinq.api.Printer
import org.kotlinq.api.PrintingConfiguration
import org.kotlinq.dsl.fragment
import org.kotlinq.dsl.query


class TrendingQuery(val fragment: Fragment) {


  class Builder {
    private var order: LanguageOrder =
        LanguageOrder.builder().direction(OrderDirection.DESC).build()!!
    private var first: Int? = null
    private var after: String? = null

    fun order(it: LanguageOrder) = apply { order = it }
    fun first(it: Int) = apply { first = it }
    fun after(it: String?) = apply { after = it }

    fun build(queryString: String): TrendingQuery {
      val order = "order" to order.direction().name
      val first = first?.let { "first" to it }
      val after = after?.let { "after" to it }

      return listOfNotNull(
          first,
          after,
          order,
          "type" to "REPOSITORY",
          "query" to queryString)
          .toMap()
          .let(::createQuery)
          .let(::TrendingQuery)
    }
  }

  companion object {

    fun compatibleRequestString(fragment: Fragment): String =
        printer.printFragmentToString(fragment).let {
          """{"query": "$it"}""".trimIndent()
        }

    private val printer = PrintingConfiguration.DEFAULT
        .toBuilder()
        .metaStrategy(Printer.MetaStrategy.builder()
            .includeTypename { it.typeName != "Query" }
            .includeId { it.typeName != "Query" }
            .build())
        .build()
        .let(Printer.Companion::fromConfiguration)

    private fun createQuery(arguments: Map<String, Any>) = query {
      "search"(arguments) on def("SearchResultItemConnection") {
        "repositoryCount"(integer)
        "pageInfo" on def("PageInfo") {
          "hasNextPage"(boolean)
          "endCursor"(!string)
        }
        "nodes"..{
          on..repositoryFragment(arguments["order"] as String)
        }
      }
    }

    private
    fun repositoryFragment(order: String) = fragment("Repository") {
      "name"(string)
      "createdAt"(string) // soon fix
      "description"(string)
      "url"(string)
      "languages"("first" to 1, "orderBy" to order) on def("LanguageConnection") {
        "nodes" on def("Language") {
          "name"(string)
        }
      }
      "licenseInfo" on def("License") {
        "name"(string)
      }
      "owner" on def("Actor") {
        "name"(string)
      }
      "stargazers" on def("StargazerConnection") {
        "totalCount"(integer)
      }
    }

  }
}